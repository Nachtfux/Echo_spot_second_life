"""Evaluate trained wake/stop word models the way Spot Voice runs them.

    .venv/bin/python evaluate.py out/models/claude/hey_claude.onnx [more.onnx ...]

The openWakeWord pipeline is run by hand with onnxruntime, exactly like its streaming code and
the app: per 1280-sample frame (80 ms at 16 kHz) melspectrogram.onnx on the frame plus 480 samples
of history, embedding_model.onnx on the last 76 mel frames, the classifier on the last 16 x 96
embeddings. (openwakeword's Model class is not used; it does not work with the training pins.)

Reported per threshold:
  recall        share of the positive test clips that trigger (<set>/<model>/positive_test: Piper
                clips that train.py generated for testing and never trained on)
  near-miss     share of the adversarial negative test clips that trigger (negative_test: phrases
                that sound similar, e.g. "hey klaus")
  false alarms  triggers per hour on validation_set_features.npy (pre-computed embeddings of ~11 h
                of real-world audio from openWakeWord's authors)

A trigger follows the app's rule: score > threshold on 2 consecutive frames; afterwards the
detector is deaf for --refractory seconds (the app is in a voice session then).
Clean synthetic speech is easier than a real room, so read recall as an upper bound; for a harder
test mix in music: --background data/fma --snr 5.
"""
import argparse
import glob
import os
import random
import sys
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
SR, CHUNK, OVERLAP, MEL_FRAMES, EMB_DIM = 16000, 1280, 480, 76, 96
LEAD, TAIL = 1.0, 1.0          # seconds of padding before/after a clip (pipeline warm-up, time to trigger)


def parse_args():
    data = os.environ.get("OWW_DATA_DIR", os.path.join(HERE, "data"))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("models", nargs="+", help="trained classifier(s), e.g. out/models/claude/hey_claude.onnx")
    ap.add_argument("--thresholds", type=float, nargs="+", default=[0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9])
    ap.add_argument("--frames", type=int, default=2, help="consecutive frames above threshold (app: 2)")
    ap.add_argument("--refractory", type=float, default=2.0, help="seconds without triggers after one")
    ap.add_argument("--positive", help="dir of 16 kHz WAVs that should trigger (default: <model>/positive_test)")
    ap.add_argument("--negative", help="dir of 16 kHz WAVs that should not (default: <model>/negative_test)")
    ap.add_argument("--max-clips", type=int, default=2000, help="random sample per dir, 0 = all (default 2000)")
    ap.add_argument("--val", default=os.path.join(data, "validation_set_features.npy"),
                    help="negative embeddings, shape (frames, 96) (default: data/validation_set_features.npy)")
    ap.add_argument("--feature-models", default=os.path.join(HERE, "openwakeword", "openwakeword", "resources", "models"),
                    help="dir with melspectrogram.onnx and embedding_model.onnx")
    ap.add_argument("--background", help="dir of 16 kHz WAVs (e.g. data/fma) mixed into the clips")
    ap.add_argument("--snr", type=float, default=10.0, help="speech-to-background ratio in dB (default 10)")
    return ap.parse_args()


def load_wav(path):
    with wave.open(path, "rb") as w:
        if (w.getframerate(), w.getnchannels(), w.getsampwidth()) != (SR, 1, 2):
            raise SystemExit(f"{path}: need 16 kHz mono 16-bit WAV")
        return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32)


def session(model):
    return ort.InferenceSession(model, providers=["CPUExecutionProvider"])


def load_classifier(path):
    """Classifier with a dynamic batch size (train.py exports it with batch size 1)."""
    m = onnx.load(path)
    n_emb = m.graph.input[0].type.tensor_type.shape.dim[1].dim_value or 16
    for v in list(m.graph.input) + list(m.graph.output):
        v.type.tensor_type.shape.dim[0].dim_param = "batch"
    s = session(m.SerializeToString())
    name = s.get_inputs()[0].name
    return (lambda x: s.run(None, {name: x.astype(np.float32)})[0].reshape(-1)), n_emb


def stream_scores(audio, mel, emb, clf, n_emb):
    """Per-frame scores for a batch of equally long clips (B, samples), as openWakeWord streams them."""
    b, n_chunks = audio.shape[0], audio.shape[1] // CHUNK
    mel_win = np.ones((b, MEL_FRAMES, 32), np.float32)     # openWakeWord starts with ones
    feats = np.zeros((b, n_emb, EMB_DIM), np.float32)
    scores = np.zeros((b, n_chunks), np.float32)
    for k in range(n_chunks):
        raw = audio[:, max(0, k * CHUNK - OVERLAP):(k + 1) * CHUNK]
        # One clip per call: the model's top-dB floor uses a ReduceMax over the whole batch.
        spec = np.concatenate([mel.run(None, {"input": r[None]})[0] for r in raw])[:, 0] / 10 + 2
        mel_win = np.concatenate([mel_win, spec], axis=1)[:, -MEL_FRAMES:]
        e = emb.run(None, {"input_1": mel_win[..., None]})[0].reshape(b, 1, EMB_DIM)
        feats = np.concatenate([feats, e], axis=1)[:, -n_emb:]
        if k + 1 >= n_emb:                                               # app: 0 until the window is full
            scores[:, k] = clf(feats)
    return scores


def hits(scores, thr, frames):
    """Bool array (..., n - frames + 1): frame i ends a run of `frames` scores above thr."""
    hot = scores > thr
    return np.lib.stride_tricks.sliding_window_view(hot, frames, axis=-1).all(-1)


def clip_scores(paths, mel, emb, clf, n_emb, background, snr, rng):
    """Frame scores of each clip, padded with LEAD/TAIL seconds (clips batched by length)."""
    paths = sorted(paths, key=os.path.getsize)
    out = []
    for i in range(0, len(paths), 256):
        clips = [load_wav(p) for p in paths[i:i + 256]]
        n = int(LEAD * SR) + max(len(c) for c in clips) + int(TAIL * SR)
        n += -n % CHUNK
        batch = rng.normal(0, 5, (len(clips), n)).astype(np.float32)   # faint noise instead of digital zero
        for j, c in enumerate(clips):
            batch[j, int(LEAD * SR):int(LEAD * SR) + len(c)] += c
            if background:
                bg = background[rng.integers(len(background))]
                start = rng.integers(len(bg))
                bg = np.tile(bg, n // len(bg) + 2)[start:start + n]
                gain = np.sqrt(np.mean(c ** 2) / max(np.mean(bg ** 2), 1e-9)) / 10 ** (snr / 20)
                batch[j] += gain * bg
        out.extend(stream_scores(np.clip(batch, -32768, 32767), mel, emb, clf, n_emb))
        if sys.stderr.isatty():
            print(f"\r  {min(i + 256, len(paths))}/{len(paths)} clips", end="", file=sys.stderr, flush=True)
    if sys.stderr.isatty():
        print("\r" + " " * 40 + "\r", end="", file=sys.stderr)
    return out


def show(path):
    rel = os.path.relpath(path)
    return path if rel.startswith("..") else rel


def find_dir(model, kind, override):
    if override:
        return override
    name = os.path.splitext(os.path.basename(model))[0]   # train.py writes <out>/<name>/<kind>/
    candidates = [os.path.join(os.path.dirname(model), name, kind)]
    candidates += sorted(glob.glob(os.path.join(HERE, "out", "*", name, kind)))
    for d in candidates:
        if glob.glob(os.path.join(d, "*.wav")):
            return d
    return None


def sample(d, n):
    files = sorted(glob.glob(os.path.join(d, "*.wav")))
    return random.Random(0).sample(files, n) if 0 < n < len(files) else files


def main():
    args = parse_args()
    global np, onnx, ort   # imported only now, so that --help works without the training venv
    import numpy as np
    import onnx
    import onnxruntime as ort

    mel = session(os.path.join(args.feature_models, "melspectrogram.onnx"))
    emb = session(os.path.join(args.feature_models, "embedding_model.onnx"))
    val = np.load(args.val, mmap_mode="r") if os.path.exists(args.val) else None
    background = None
    if args.background:
        files = sorted(glob.glob(os.path.join(args.background, "*.wav")))
        background = [load_wav(f) for f in random.Random(0).sample(files, min(50, len(files)))]
        if not background:
            raise SystemExit(f"no WAV files in {args.background}")
    refractory = max(args.frames, int(round(args.refractory * SR / CHUNK)))

    for model in args.models:
        clf, n_emb = load_classifier(model)
        rng = np.random.default_rng(0)
        cols, info = {}, []
        for kind, override, label in (("positive_test", args.positive, "recall"),
                                      ("negative_test", args.negative, "near-miss")):
            d = find_dir(model, kind, override)
            if d:
                files = sample(d, args.max_clips)
                per_clip = clip_scores(files, mel, emb, clf, n_emb, background, args.snr, rng)
                cols[label] = [np.mean([hits(s, t, args.frames).any() for s in per_clip]) for t in args.thresholds]
                info.append(f"{len(files)} {kind.split('_')[0]} clips from {show(d)}")
        if val is not None:
            windows = np.lib.stride_tricks.sliding_window_view(val, (n_emb, EMB_DIM))[:, 0]
            scores = np.concatenate([clf(np.ascontiguousarray(windows[i:i + 8192]))
                                     for i in range(0, len(windows), 8192)])
            hours = val.shape[0] * CHUNK / SR / 3600
            fa = []
            for t in args.thresholds:
                n, last = 0, -refractory
                for i in np.flatnonzero(hits(scores, t, args.frames)):
                    if i - last >= refractory:
                        n, last = n + 1, i
                fa.append(n / hours)
            cols["false alarms/h"] = fa
            info.append(f"{hours:.1f} h of negative features from {show(args.val)}")
        else:
            info.append(f"no false-alarm test: {args.val} not found")

        print(f"\n{os.path.basename(model)}  (input {n_emb} x {EMB_DIM}; trigger: {args.frames} frames > threshold"
              f"{f'; background {args.snr:g} dB SNR' if background else ''})")
        for line in info:
            print("  " + line)
        print("  threshold" + "".join(f"{c:>16}" for c in cols))
        for k, t in enumerate(args.thresholds):
            row = "".join(f"{cols[c][k]:>16.2f}" if c.startswith("false") else f"{100 * cols[c][k]:>14.1f} %"
                          for c in cols)
            print(f"  {t:>9.2f}{row}")


if __name__ == "__main__":
    main()
