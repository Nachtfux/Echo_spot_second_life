#!/bin/bash
# Trains hey_claude.onnx and danke_claude.onnx with openWakeWord's train.py on the Mac,
# both models in parallel (one pipeline each, CPU threads split between them).
# Usage: run_train.sh [n_samples] [steps]   (defaults 10000 / 20000)
cd "$(dirname "$0")" || exit 1
. .venv/bin/activate
export PYTORCH_ENABLE_MPS_FALLBACK=1   # torch 2.1 lacks a weight-norm op on Apple GPUs
export PYTORCH_JIT=0                   # TorchScript fuser fails on MPS (Piper's fused_add_tanh...)
export PYTHONWARNINGS=ignore
export OMP_NUM_THREADS=5 MKL_NUM_THREADS=5
python make_configs.py "${1:-10000}" "${2:-20000}" || exit 1

pipeline() {
  local m=$1
  for step in --generate_clips --augment_clips --train_model; do
    echo "===== $m $step $(date +%T)"
    # --train_model exits non-zero at the optional TFLite export; the .onnx is written before that.
    python openwakeword/openwakeword/train.py --training_config "$m.yaml" "$step" || echo "STEP_EXIT_NONZERO $m $step"
  done
  if [ -s "out/$m.onnx" ]; then echo "MODEL_OK $m $(wc -c < "out/$m.onnx") bytes $(date +%T)"; else echo "MODEL_MISSING $m"; fi
}

set -- ${OWW_MODELS:-hey_claude danke_claude}
pipeline "$1" > "train_$1.log" 2>&1 &
sleep 20   # let the first one load its Piper model before the second starts
pipeline "$2" > "train_$2.log" 2>&1 &
wait
echo "ALL_DONE $(date +%T)"
