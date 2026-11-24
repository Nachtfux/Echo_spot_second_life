"""Background audio for augmentation: MIT room impulse responses + 2 h Free Music Archive (16 kHz).

AudioSet from the official notebook is skipped to save disk space and time.
"""
import os

import datasets
import numpy as np
import scipy.io.wavfile
from tqdm import tqdm

os.chdir(os.path.dirname(os.path.abspath(__file__)))

if not os.path.isdir("mit_rirs") or not os.listdir("mit_rirs"):
    os.makedirs("mit_rirs", exist_ok=True)
    for row in tqdm(datasets.load_dataset("davidscripka/MIT_environmental_impulse_responses",
                                          split="train", streaming=True), desc="rirs"):
        name = row["audio"]["path"].split("/")[-1]
        scipy.io.wavfile.write(os.path.join("mit_rirs", name), 16000,
                               (row["audio"]["array"] * 32767).astype(np.int16))

HOURS = 4
if len(os.listdir("fma") if os.path.isdir("fma") else []) < HOURS * 3600 // 30:
    os.makedirs("fma", exist_ok=True)
    fma = iter(datasets.load_dataset("rudraml/fma", name="small", split="train", streaming=True)
               .cast_column("audio", datasets.Audio(sampling_rate=16000)))
    for _ in tqdm(range(HOURS * 3600 // 30), desc="fma"):
        row = next(fma)
        name = row["audio"]["path"].split("/")[-1].replace(".mp3", ".wav")
        if os.path.exists(os.path.join("fma", name)):
            continue
        scipy.io.wavfile.write(os.path.join("fma", name), 16000,
                               (row["audio"]["array"] * 32767).astype(np.int16))

print("PREP_DONE", {d: len(os.listdir(d)) for d in ["mit_rirs", "fma"]})
