"""Write openWakeWord training configs (one YAML per model) for a word set.

    python make_configs.py --words words/claude.yaml [n_samples] [steps]

Defaults are our "v2" settings: 20000 samples, 50000 steps, layer size 64, 2 augmentation rounds,
max negative weight 2000 (override with OWW_LAYER, OWW_AUG_ROUNDS, OWW_NEG_WEIGHT).
Configs go to out/<set>/<model>.yaml and model names to out/<set>/models.txt.

Clips and features from an earlier run are reused by openWakeWord's train.py. So that they are
never stale, this script also removes them when the phrases (clips) or the sample/augmentation
settings (features) changed since the run that made them.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import sys

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))


def load_words(path):
    with open(path, encoding="utf-8") as f:
        w = yaml.safe_load(f) or {}
    models = w.get("models") or {}
    if not 1 <= len(models) <= 2:
        sys.exit(f"{path}: 'models' needs one or two entries, found {len(models)}")
    for name, m in models.items():
        if not re.fullmatch(r"[A-Za-z0-9_-]+", str(name)):
            sys.exit(f"{path}: model name '{name}' may only contain letters, digits, _ and -")
        if not (m or {}).get("target_phrase"):
            sys.exit(f"{path}: model '{name}' has no target_phrase")
    w.setdefault("name", os.path.splitext(os.path.basename(path))[0])
    return w


def show(path):
    rel = os.path.relpath(path, HERE)
    return path if rel.startswith("..") else rel


def fingerprint(obj):
    return hashlib.sha256(json.dumps(obj, sort_keys=True).encode()).hexdigest()[:16]


def drop_stale(stamp_file, stamp, paths, what):
    """Delete `paths` if `stamp_file` holds a different stamp (= they were made with other settings)."""
    old = open(stamp_file).read().strip() if os.path.exists(stamp_file) else None
    if old not in (None, stamp):
        for p in paths:
            if os.path.exists(p):
                print(f"  {what} changed since the last run: removing {show(p)}", flush=True)
                shutil.rmtree(p) if os.path.isdir(p) else os.remove(p)
    with open(stamp_file, "w") as f:
        f.write(stamp + "\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--words", required=True, help="word set file, e.g. words/claude.yaml")
    ap.add_argument("n_samples", nargs="?", type=int, default=20000, help="clips per model (default 20000)")
    ap.add_argument("steps", nargs="?", type=int, default=50000, help="training steps (default 50000)")
    ap.add_argument("--data-dir", default=os.environ.get("OWW_DATA_DIR", os.path.join(HERE, "data")),
                    help="features + background audio (default: data/, or $OWW_DATA_DIR)")
    ap.add_argument("--out-dir", help="where configs and training output go (default: out/<set>)")
    args = ap.parse_args()

    words = load_words(args.words)
    data = os.path.abspath(args.data_dir)
    out = os.path.abspath(args.out_dir or os.path.join(HERE, "out", words["name"]))
    os.makedirs(out, exist_ok=True)

    # Negative features: one or two ACAV100M subsets (the second fetched with a shifted phase).
    features = {"ACAV100M_sample": os.path.join(data, "acav_subset.npy")}
    batch = {"ACAV100M_sample": 1024, "adversarial_negative": 50, "positive": 50}
    if os.path.exists(os.path.join(data, "acav_subset2.npy")):
        features["ACAV100M_sample2"] = os.path.join(data, "acav_subset2.npy")
        batch = {"ACAV100M_sample": 512, "ACAV100M_sample2": 512, "adversarial_negative": 50, "positive": 50}

    common = dict(
        output_dir=out,
        rir_paths=[os.path.join(data, "mit_rirs")],
        background_paths=[os.path.join(data, "fma")],
        background_paths_duplication_rate=[1],
        false_positive_validation_data_path=os.path.join(data, "validation_set_features.npy"),
        feature_data_files=features,
        batch_n_per_class=batch,
        piper_sample_generator_path=os.path.join(HERE, "piper_shim"),
        tts_batch_size=32,
        augmentation_batch_size=16,   # value from openwakeword/examples/custom_model.yml
        model_type="dnn",
        layer_size=int(os.environ.get("OWW_LAYER", 64)),
        augmentation_rounds=int(os.environ.get("OWW_AUG_ROUNDS", 2)),
        max_negative_weight=int(os.environ.get("OWW_NEG_WEIGHT", 2000)),
        target_false_positives_per_hour=0.2,
        n_samples=args.n_samples,
        n_samples_val=max(1000, args.n_samples // 5),
        steps=args.steps,
    )

    voice_words = {k: words.get(k) for k in ("german_words", "pronunciations")}   # read by the shim / data.py
    for name, m in words["models"].items():
        cfg = dict(common)
        cfg.update(m)            # target_phrase, custom_negative_phrases (+ any train.py option)
        cfg["model_name"] = name
        cfg.setdefault("custom_negative_phrases", [])
        work = os.path.join(out, name)   # train.py's clip and feature directory for this model
        clips = fingerprint(dict(voice_words, target_phrase=cfg["target_phrase"],
                                 custom_negative_phrases=cfg["custom_negative_phrases"]))
        drop_stale(os.path.join(out, f".{name}.clips"), clips, [work], "phrases")
        feats = fingerprint([clips] + [cfg[k] for k in ("n_samples", "n_samples_val", "augmentation_rounds")])
        drop_stale(os.path.join(out, f".{name}.features"), feats,
                   [os.path.join(work, f"{kind}_features_{split}.npy")
                    for kind in ("positive", "negative") for split in ("train", "test")],
                   "sample/augmentation settings")
        with open(os.path.join(out, f"{name}.yaml"), "w") as f:
            yaml.dump(cfg, f)
        print(f"{name}: {cfg['n_samples']} samples ({cfg['n_samples_val']} test), {cfg['steps']} steps, "
              f"layer {cfg['layer_size']} -> {show(os.path.join(out, name + '.yaml'))}")
        print(f"  {cfg['target_phrase']}")
    with open(os.path.join(out, "models.txt"), "w") as f:
        f.write("\n".join(words["models"]) + "\n")


if __name__ == "__main__":
    main()
