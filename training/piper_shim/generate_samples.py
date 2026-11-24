"""train.py-compatible generate_samples() on top of rhasspy/piper-sample-generator v3 (package layout).

openWakeWord's train.py imports `generate_samples` from the directory given as
piper_sample_generator_path and calls it without a model argument (old dscripka fork API).
This shim
  * routes German-looking texts ("danke", "klohd", "stopp", ...) to the German multi-speaker
    generator (de_DE-mls, 236 speakers) and everything else to English LibriTTS-R (904 speakers),
  * splits max_samples between the two in proportion to the number of texts,
  * always writes unique file names (Piper numbers files from 0 otherwise -> overwrites),
  * resamples the 22.05 kHz output to the 16 kHz openWakeWord expects.
"""
import os
import sys
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
GERMAN_WORDS = {
    "danke", "schoen", "dankeschoen", "dir", "klohd", "stopp", "hallo", "tschuess",
    "klaus", "claudia", "nein", "bitte", "kloss", "tschett", "dschi",
}
PASS_THROUGH = ("slerp_weights", "length_scales", "noise_scales", "noise_scale_ws", "max_speakers")
MAX_BATCH = 32   # keeps Apple-GPU (MPS) memory in check


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
        _generate(text=ts, output_dir=output_dir, model=m, max_samples=n, file_names=fn,
                  batch_size=max(1, min(int(batch_size), MAX_BATCH)), **extra)
        done += n

    for f in set(os.listdir(output_dir)) - before:
        if f.endswith(".wav"):
            _to_16k(os.path.join(output_dir, f))
