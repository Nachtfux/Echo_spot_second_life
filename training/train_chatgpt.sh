#!/bin/bash
# Trains hey_chatgpt.onnx + danke_chatgpt.onnx from scratch (data was deleted in the cleanup).
cd "$(dirname "$0")" || exit 1
. .venv/bin/activate
echo "== chatgpt start $(date +%T)"
R=https://github.com/rhasspy/piper-sample-generator/releases/download/v2.0.0
for m in en_US-libritts_r-medium de_DE-mls-medium; do
  [ -s "piper-sample-generator/models/$m.pt" ] || curl -fsSL -o "piper-sample-generator/models/$m.pt" "$R/$m.pt" || { echo "PIPER_DL_FAILED $m"; exit 1; }
done
HF=https://huggingface.co/datasets/davidscripka/openwakeword_features/resolve/main
[ -s validation_set_features.npy ] || curl -fsSL -o validation_set_features.npy "$HF/validation_set_features.npy" || { echo "VAL_DL_FAILED"; exit 1; }
[ -s acav_subset.npy ] || python3 -I fetch_features.py acav_subset.npy 4 0 || { echo "FETCH_FAILED 1"; exit 1; }
[ -s acav_subset2.npy ] || python3 -I fetch_features.py acav_subset2.npy 4 0.5 || { echo "FETCH_FAILED 2"; exit 1; }
python prep_data.py 2>&1 | grep -E 'PREP_DONE|Error' || true
export OWW_WORDS=chatgpt OWW_MODELS="hey_chatgpt danke_chatgpt" OWW_LAYER=64 OWW_AUG_ROUNDS=2 OWW_NEG_WEIGHT=2000
./run_train.sh 20000 50000
mkdir -p models/chatgpt-v1 && cp out/hey_chatgpt.onnx out/danke_chatgpt.onnx models/chatgpt-v1/ 2>/dev/null
echo "== chatgpt end $(date +%T)"
