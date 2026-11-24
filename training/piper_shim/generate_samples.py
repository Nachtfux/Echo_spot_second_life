"""train.py-compatible generate_samples() on top of rhasspy/piper-sample-generator v3 (package layout).

openWakeWord's train.py imports `generate_samples` from the directory given as
piper_sample_generator_path and calls it without a model argument (old dscripka fork API).
This shim
  * routes German-looking texts ("danke", "klohd", "stopp", ...) to the German multi-speaker
    generator (de_DE-mls, 236 speakers) and everything else to English LibriTTS-R (904 speakers);
    a text is German if it contains a word of the "german_words" list of the word file named by
    $OWW_WORDS_FILE (set by train.sh), or of the built-in list below,
  * splits max_samples between the two in proportion to the number of texts,
  * always writes unique file names (Piper numbers files from 0 otherwise -> overwrites),
  * resamples the 22.05 kHz output to the 16 kHz openWakeWord expects.
"""
import itertools
import multiprocessing
import os
import sys
import types
import uuid
import wave
from math import gcd

import numpy as np
from scipy.signal import resample_poly

HERE = os.path.dirname(os.path.abspath(__file__))
PSG = os.path.join(os.path.dirname(HERE), "piper-sample-generator")
sys.path.insert(0, PSG)
from piper_sample_generator.__main__ import generate_samples as _generate  # noqa: E402

MODEL_EN = os.path.join(PSG, "models", "en_US-libritts_r-medium.pt")
MODEL_DE = os.path.join(PSG, "models", "de_DE-mls-medium.pt")
# Default list; replaced by the "german_words" of the word file in $OWW_WORDS_FILE, if it has one.
GERMAN_WORDS = {
    "danke", "schoen", "dankeschoen", "dir", "klohd", "stopp", "hallo", "tschuess",
    "klaus", "claudia", "nein", "bitte", "kloss", "tschett", "dschi",
}
if os.environ.get("OWW_WORDS_FILE"):
    import yaml
    with open(os.environ["OWW_WORDS_FILE"], encoding="utf-8") as _f:
        _words = (yaml.safe_load(_f) or {}).get("german_words")
    if _words:
        GERMAN_WORDS = {str(w).lower() for w in _words}
PASS_THROUGH = ("slerp_weights", "length_scales", "noise_scales", "noise_scale_ws", "max_speakers")
MAX_BATCH = 32   # keeps Apple-GPU (MPS) memory in check
# PyTorch's MPS backend caches a compiled graph per input shape and never frees it. Thousands of
# different adversarial texts (= thousands of shapes) grew each training process to ~50 GB and
# filled swap + disk on a 36 GB Mac. So clips are generated in chunks, each in a fresh process
# whose memory goes back to the OS when it exits.
CHUNK = int(os.environ.get("OWW_CLIP_CHUNK", "1500"))


def _is_german(t: str) -> bool:
    words = t.lower().replace(",", " ").replace("!", " ").replace(".", " ").split()
    return any(w in GERMAN_WORDS for w in words)


def _to_16k(path: str) -> None:
    with wave.open(path, "rb") as w:
        sr = w.getframerate()
        data = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16)
    if sr == 16000 or data.size == 0:
        return
    g = gcd(sr, 16000)
    y = resample_poly(data.astype(np.float32), 16000 // g, sr // g)
    y = np.clip(np.round(y), -32768, 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(y.tobytes())


def _chunk_worker(texts, output_dir, model, n, file_names, batch_size, extra, chunk_index):
    """Runs in a fresh process. Starts at a different first speaker per chunk so that chunking does
    not shrink voice variety (Piper walks speaker pairs from speaker 0 on every call)."""
    import piper_sample_generator.__main__ as psg

    def product(*args):
        if len(args) == 2 and all(isinstance(a, range) for a in args):
            r = list(args[0])
            s = (chunk_index * 37) % len(r)
            return itertools.product(r[s:] + r[:s], args[1])
        return itertools.product(*args)

    psg.it = types.SimpleNamespace(cycle=itertools.cycle, islice=itertools.islice, product=product)
    psg.generate_samples(text=texts, output_dir=output_dir, model=model, max_samples=n,
                         file_names=file_names, batch_size=batch_size, **extra)


def generate_samples(text, output_dir, max_samples=None, file_names=None, batch_size=1,
                     auto_reduce_batch_size=False, model=None, **kw):
    if isinstance(text, str) and os.path.isfile(text):
        texts = [line.strip() for line in open(text, encoding="utf-8") if line.strip()]
    elif isinstance(text, str):
        texts = [text]
    else:
        texts = list(text)
    if max_samples is None:
        max_samples = len(texts)
    groups = [(MODEL_DE, [t for t in texts if _is_german(t)]),
              (MODEL_EN, [t for t in texts if not _is_german(t)])]
    groups = [(m, ts) for m, ts in groups if ts]
    total = sum(len(ts) for _, ts in groups)
    names = list(file_names) if file_names else []
    os.makedirs(output_dir, exist_ok=True)
    before = set(os.listdir(output_dir))
    extra = {k: v for k, v in kw.items() if k in PASS_THROUGH}

    done = 0
    for i, (m, ts) in enumerate(groups):
        n = max_samples - done if i == len(groups) - 1 else round(max_samples * len(ts) / total)
        if n <= 0:
            continue
        fn = names[done:done + n] if len(names) >= done + n else [uuid.uuid4().hex + ".wav" for _ in range(n)]
        print(f"[shim] {os.path.basename(m)}: {n} clips from {len(ts)} texts -> {output_dir}", flush=True)
        bs = max(1, min(int(batch_size), MAX_BATCH))
        ctx = multiprocessing.get_context("spawn")
        for k, start in enumerate(range(0, n, CHUNK)):
            cnt = min(CHUNK, n - start)
            off = start % len(ts)          # continue the text rotation where the last chunk stopped
            p = ctx.Process(target=_chunk_worker,
                            args=(ts[off:] + ts[:off], str(output_dir), m, cnt, fn[start:start + cnt], bs, extra, k))
            p.start()
            p.join()
            if p.exitcode != 0:
                raise RuntimeError(f"clip generation chunk {k} failed (exit code {p.exitcode})")
            for f in set(os.listdir(output_dir)) - before:      # resample this chunk's files right away
                if f.endswith(".wav"):
                    _to_16k(os.path.join(output_dir, f))
            before = set(os.listdir(output_dir))
            print(f"[shim] chunk {k + 1}: {start + cnt}/{n} clips", flush=True)
        done += n
