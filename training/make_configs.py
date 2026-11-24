"""Writes hey_claude.yaml and danke_claude.yaml for openwakeword/openwakeword/train.py."""
import copy
import os
import sys

import yaml

os.chdir(os.path.dirname(os.path.abspath(__file__)))
N_SAMPLES = int(sys.argv[1]) if len(sys.argv) > 1 else 10000
STEPS = int(sys.argv[2]) if len(sys.argv) > 2 else 30000
LAYER = int(os.environ.get("OWW_LAYER", 32))
AUG_ROUNDS = int(os.environ.get("OWW_AUG_ROUNDS", 1))
NEG_WEIGHT = int(os.environ.get("OWW_NEG_WEIGHT", 1500))
# Negative features: one or two ACAV100M subsets (second one fetched with a shifted phase).
FEATURES = {"ACAV100M_sample": "acav_subset.npy"}
BATCH = {"ACAV100M_sample": 1024, "adversarial_negative": 50, "positive": 50}
if os.path.exists("acav_subset2.npy"):
    FEATURES["ACAV100M_sample2"] = "acav_subset2.npy"
    BATCH = {"ACAV100M_sample": 512, "ACAV100M_sample2": 512, "adversarial_negative": 50, "positive": 50}

base = yaml.load(open("openwakeword/examples/custom_model.yml").read(), yaml.Loader)
common = dict(
    output_dir="./out",
    rir_paths=["./mit_rirs"],
    background_paths=["./fma"],
    background_paths_duplication_rate=[1],
    false_positive_validation_data_path="validation_set_features.npy",
    feature_data_files=FEATURES,
    batch_n_per_class=BATCH,
    piper_sample_generator_path="./piper_shim",
    tts_batch_size=32,
    model_type="dnn", layer_size=LAYER, augmentation_rounds=AUG_ROUNDS,
    max_negative_weight=NEG_WEIGHT, target_false_positives_per_hour=0.2,
    n_samples=N_SAMPLES, n_samples_val=max(1000, N_SAMPLES // 5), steps=STEPS,
)
# German-looking texts go to the German Piper voices (see piper_shim), the rest to English ones.
words = {
    "hey_claude": dict(
        target_phrase=["hey claude", "hey clode", "hey klohd", "hey, klohd", "hey klohd!"],
        custom_negative_phrases=[
            "hey klaus", "hey claudia", "hey kloss", "hallo klohd", "danke klohd",
            "hey clown", "hey cloud", "hey cleo", "hey chloe", "hey clara", "hey jarvis",
            "okay google", "hey google", "alexa", "hey siri", "hey", "claude",
        ],
    ),
    "danke_claude": dict(
        target_phrase=["danke klohd", "danke, klohd", "danke klohd!", "klohd stopp", "klohd, stopp",
                       "danke claude", "claude stop"],
        custom_negative_phrases=[
            "danke", "danke schoen", "danke dir", "danke klaus", "stopp", "klaus stopp",
            "hallo klohd", "hey klohd", "bitte klohd",
            "thank you", "thanks", "hey claude", "alexa", "hey google", "hey jarvis",
        ],
    ),
}
# ChatGPT word set (OWW_WORDS=chatgpt). German spellings go to the German voices via the shim.
chatgpt = {
    "hey_chatgpt": dict(
        target_phrase=["hey chatgpt", "hey chat g p t", "hey chat gee pee tee", "hey, chat g p t!",
                       "hey tschett dschi pi ti", "hey, tschett dschi pi ti"],
        custom_negative_phrases=[
            "hey chat", "hey chet", "hey jack", "hey check", "hey chatty", "hey cheddar",
            "chat g p t", "hey g p t", "hey dschi pi ti", "danke tschett dschi pi ti",
            "hey claude", "hey jarvis", "okay google", "hey google", "alexa", "hey siri", "hey",
        ],
    ),
    "danke_chatgpt": dict(
        target_phrase=["danke tschett dschi pi ti", "danke, tschett dschi pi ti", "tschett dschi pi ti stopp",
                       "danke chatgpt", "thanks chat g p t", "chat g p t stop", "chat gee pee tee, stop"],
        custom_negative_phrases=[
            "danke", "danke schoen", "danke dir", "stopp", "thank you", "thanks", "stop",
            "hey tschett dschi pi ti", "hey chat g p t", "bitte tschett dschi pi ti", "danke tschuess",
            "danke klohd", "hey claude", "alexa", "hey google", "hey jarvis",
        ],
    ),
}
if os.environ.get("OWW_WORDS", "claude") == "chatgpt":
    words = chatgpt
for name, w in words.items():
    cfg = copy.deepcopy(base)
    cfg.update(common)
    cfg.update(w)
    cfg["model_name"] = name
    with open(f"{name}.yaml", "w") as f:
        yaml.dump(cfg, f)
    print(name, cfg["n_samples"], cfg["n_samples_val"], cfg["steps"], cfg["target_phrase"])
