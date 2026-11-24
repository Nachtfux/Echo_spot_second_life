#!/bin/bash
# v2: more clips, 8 GB negatives, 4 h background, bigger model, more steps. Reuses v1 clips.
cd "$(dirname "$0")" || exit 1
. .venv/bin/activate
echo "== v2 start $(date +%T)"
[ -s acav_subset2.npy ] || python3 -I fetch_features.py acav_subset2.npy 4 0.5 || { echo "FETCH_FAILED"; exit 1; }
python prep_data.py 2>&1 | grep -E 'PREP_DONE|Error' || true
rm -f out/hey_claude/*.npy out/danke_claude/*.npy out/*.onnx    # re-augment with the new clips
export OWW_LAYER=64 OWW_AUG_ROUNDS=2 OWW_NEG_WEIGHT=2000
./run_train.sh 20000 50000
echo "== v2 end $(date +%T)"
