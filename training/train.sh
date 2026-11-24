#!/usr/bin/env bash
# Train the wake word and the stop word of one word set with openWakeWord, in one command.
#
#   ./train.sh claude               words/claude.yaml  -> hey_claude.onnx  + danke_claude.onnx
#   ./train.sh chatgpt              words/chatgpt.yaml -> hey_chatgpt.onnx + danke_chatgpt.onnx
#   ./train.sh words/mine.yaml      your own word set (template: words/example-custom.yaml)
#   ./train.sh claude --quick       smoke test: 5000 samples / 10000 steps (not for daily use)
#
# Runs setup.sh (first time only), downloads the training data, writes the configs and trains
# both models in parallel (Piper clips -> augmentation -> training). Results: out/models/<set>/.
# Re-running continues where it stopped: downloads, clips and features are reused.
# Needs ~20 GB free disk; a full run takes ~1.5-2.5 h on an Apple Silicon Mac (--quick ~30-45 min).
#
# Environment: OWW_DATA_DIR (data folder, default training/data; can be shared between sets),
#              OWW_LAYER, OWW_AUG_ROUNDS, OWW_NEG_WEIGHT (hyperparameters, defaults 64 / 2 / 2000).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)

say() { printf '\n== %s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
usage() { sed -n '2,15p' "$HERE/train.sh" | sed 's/^# \{0,1\}//'; exit "$1"; }
sha256() { if command -v shasum >/dev/null; then shasum -a 256 "$1"; else sha256sum "$1"; fi | cut -d' ' -f1; }
free_gb() { df -Pk "$1" | awk 'NR == 2 { print int($4 / 1048576) }'; }

SET="" QUICK=0
for a in "$@"; do
  case $a in
    --quick) QUICK=1 ;;
    -h|--help) usage 0 ;;
    -*) echo "unknown option: $a" >&2; usage 2 ;;
    *) [ -z "$SET" ] || usage 2; SET=$a ;;
  esac
done
[ -n "$SET" ] || usage 2
case $SET in
  claude|chatgpt) WORDS=$HERE/words/$SET.yaml ;;
  *) [ -f "$SET" ] || die "word file not found: $SET"
     WORDS=$(cd "$(dirname "$SET")" && pwd)/$(basename "$SET") ;;
esac
DATA=${OWW_DATA_DIR:-$HERE/data}
mkdir -p "$DATA"
DATA=$(cd "$DATA" && pwd)
cd "$HERE"
mkdir -p out
if [ "$QUICK" = 1 ]; then N_SAMPLES=5000 STEPS=10000; else N_SAMPLES=20000 STEPS=50000; fi

say "1/5 Setup"
if [ "$(cat .venv/.setup-complete 2>/dev/null)" = "$(cat setup.sh constraints.txt patches/*.patch | cksum)" ]; then
  echo "already done (run ./setup.sh again to repair the environment)"
else
  ./setup.sh
fi
PY=$HERE/.venv/bin/python
NAME=$("$PY" -c 'import os, sys, yaml
w = yaml.safe_load(open(sys.argv[1], encoding="utf-8")) or {}
print(w.get("name") or os.path.splitext(os.path.basename(sys.argv[1]))[0])' "$WORDS")
case $NAME in *[!A-Za-z0-9_-]*|"") die "set name '$NAME' may only contain letters, digits, _ and -" ;; esac
OUT=$HERE/out/$NAME

say "2/5 Training data in $DATA"
need=2; [ "$QUICK" = 1 ] || need=8                       # generated clips + features (out/)
[ "$(free_gb out)" -ge "$need" ] || die "out/ needs ~$need GB free disk, only $(free_gb out) GB left"
need=1; for f in acav_subset.npy acav_subset2.npy; do [ -s "$DATA/$f" ] || need=$((need + 5)); done
[ "$(free_gb "$DATA")" -ge "$need" ] || die "$DATA needs ~$need GB free disk, only $(free_gb "$DATA") GB left"
VAL=$DATA/validation_set_features.npy                    # ~11 h of negative audio (pre-computed features)
if [ ! -s "$VAL" ]; then
  curl -fL --retry 3 --progress-bar -o "$VAL.part" \
    https://huggingface.co/datasets/davidscripka/openwakeword_features/resolve/main/validation_set_features.npy
  [ "$(sha256 "$VAL.part")" = a56a8a0f8e0efb91900acc6de4c0cdf4c564842e8475a7d49b36c039e17a690f ] \
    || die "checksum mismatch: $VAL.part"
  mv "$VAL.part" "$VAL"
fi
# Negative features: two disjoint 4 GB parts of the 16.5 GB ACAV100M set (HTTP range requests).
[ -s "$DATA/acav_subset.npy" ] || "$PY" -I fetch_features.py "$DATA/acav_subset.npy" 4 0
[ -s "$DATA/acav_subset2.npy" ] || "$PY" -I fetch_features.py "$DATA/acav_subset2.npy" 4 0.5
"$PY" prep_data.py "$DATA"                                # room impulse responses + 4 h of music

say "3/5 Configs for set '$NAME' ($N_SAMPLES samples, $STEPS steps)"
"$PY" make_configs.py --words "$WORDS" --data-dir "$DATA" --out-dir "$OUT" "$N_SAMPLES" "$STEPS"
MODELS=$(cat "$OUT/models.txt")

say "4/5 Training $(echo "$MODELS" | tr '\n' ' ')in parallel"
export OWW_WORDS_FILE=$WORDS            # read by piper_shim (german_words) and patched data.py (pronunciations)
export PYTORCH_ENABLE_MPS_FALLBACK=1    # torch 2.1 lacks ops Piper needs (weight norm) on Apple GPUs -> CPU
export PYTORCH_JIT=0                    # the TorchScript fuser fails on MPS (Piper's fused_add_tanh_sigmoid_multiply)
export PYTHONWARNINGS=ignore            # torch/torchaudio deprecation warnings would flood the logs
NCPU=$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 8)
THREADS=$((NCPU / 2 > 0 ? NCPU / 2 : 1))   # two trainings share the CPU
export OMP_NUM_THREADS=$THREADS MKL_NUM_THREADS=$THREADS
if command -v caffeinate >/dev/null; then caffeinate -i -w $$ & fi   # macOS: no sleep while training

pipeline() {   # generate clips -> augment -> train; train.py skips work that is already done
  local m=$1 step
  for step in --generate_clips --augment_clips --train_model; do
    echo "===== $m $step $(date +%T)"
    "$PY" openwakeword/openwakeword/train.py --training_config "$OUT/$m.yaml" "$step" \
      || { echo "FAILED $m $step"; return 1; }
  done
  echo "===== $m done $(date +%T)"
}

PIDS=""
trap 'for p in $PIDS; do pkill -P "$p" 2>/dev/null; kill "$p" 2>/dev/null; done; exit 130' INT TERM
for m in $MODELS; do
  [ -z "$PIDS" ] || sleep 20            # let the first one load its Piper voice before the next starts
  pipeline "$m" > "$OUT/train_$m.log" 2>&1 &
  PIDS="$PIDS $!"
  echo "$m: log in out/$NAME/train_$m.log"
done
alive() { local p; for p in $PIDS; do kill -0 "$p" 2>/dev/null && return 0; done; return 1; }
last=""
while alive; do                         # one status line whenever a model starts a new step
  now=""
  for m in $MODELS; do now="$now  $m: $({ grep '^=====' "$OUT/train_$m.log" || true; } | tail -1 | cut -d' ' -f3)"; done
  [ "$now" = "$last" ] || printf '%s%s\n' "$(date +%H:%M)" "$now"
  last=$now
  sleep 15
done
FAILED=0
for p in $PIDS; do wait "$p" || FAILED=1; done
trap - INT TERM

say "5/5 Results"
mkdir -p "out/models/$NAME"
for m in $MODELS; do
  if [ -s "$OUT/$m.onnx" ]; then cp "$OUT/$m.onnx" "out/models/$NAME/"; echo "out/models/$NAME/$m.onnx"
  else echo "$m: no model, see out/$NAME/train_$m.log"; FAILED=1; fi
done
[ "$FAILED" = 0 ] || die "training failed (logs: out/$NAME/train_*.log). Re-run the same command to continue."
# shellcheck disable=SC2046  # one argument per model
"$PY" evaluate.py $(for m in $MODELS; do echo "$OUT/$m.onnx"; done) || echo "(evaluation failed; models are fine)"

[ "$QUICK" = 0 ] || echo "
Note: --quick models only prove that the pipeline works. Train without --quick for real use."
cat <<EOF

Next steps
  Deploy with the installer (Spot Voice loads hey_<assistant>.onnx and danke_<assistant>.onnx):
    cp out/models/$NAME/*.onnx ../models/ && cd .. && ./install.sh voice
  or push the files yourself and let the app reload them:
    adb push out/models/$NAME/<model>.onnx /sdcard/Android/data/ai.pocket.heyclaude/files/
    adb shell am broadcast -a ai.pocket.heyclaude.SET_ASSISTANT -p ai.pocket.heyclaude --es assistant <claude|chatgpt>
  Evaluate again later (other thresholds etc.):
    .venv/bin/python evaluate.py out/models/$NAME/*.onnx
EOF
