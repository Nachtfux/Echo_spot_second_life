"""Background audio for augmentation: MIT room impulse responses + 4 h Free Music Archive (16 kHz).

    python prep_data.py [data_dir]      (default: data/ next to this script)

Writes <data_dir>/mit_rirs/*.wav and <data_dir>/fma/*.wav (30 s clips), streamed from the
Hugging Face datasets davidscripka/MIT_environmental_impulse_responses and rudraml/fma.
Re-running only fills in what is missing. AudioSet from the official notebook is skipped
to save disk space and time.
"""
import os
import sys

import datasets
import numpy as np
import scipy.io.wavfile
from tqdm import tqdm

DATA = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else
                       os.path.join(os.path.dirname(os.path.abspath(__file__)), "data"))
RIRS = os.path.join(DATA, "mit_rirs")
FMA = os.path.join(DATA, "fma")
HOURS = 4

if not os.path.isdir(RIRS) or not os.listdir(RIRS):
    os.makedirs(RIRS, exist_ok=True)
    for row in tqdm(datasets.load_dataset("davidscripka/MIT_environmental_impulse_responses",
                                          split="train", streaming=True), desc="rirs"):
        name = row["audio"]["path"].split("/")[-1]
        scipy.io.wavfile.write(os.path.join(RIRS, name), 16000,
                               (row["audio"]["array"] * 32767).astype(np.int16))

if len(os.listdir(FMA) if os.path.isdir(FMA) else []) < HOURS * 3600 // 30:
    os.makedirs(FMA, exist_ok=True)
    fma = iter(datasets.load_dataset("rudraml/fma", name="small", split="train", streaming=True)
               .cast_column("audio", datasets.Audio(sampling_rate=16000)))
    for _ in tqdm(range(HOURS * 3600 // 30), desc="fma"):
        row = next(fma)
        name = row["audio"]["path"].split("/")[-1].replace(".mp3", ".wav")
        if os.path.exists(os.path.join(FMA, name)):
            continue
        scipy.io.wavfile.write(os.path.join(FMA, name), 16000,
                               (row["audio"]["array"] * 32767).astype(np.int16))

print("PREP_DONE", {d: len(os.listdir(os.path.join(DATA, d))) for d in ["mit_rirs", "fma"]})
