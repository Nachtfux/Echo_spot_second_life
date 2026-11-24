#!/usr/bin/env bash
# One-time setup of the wake-word training environment. Idempotent: re-run it any time,
# finished steps are skipped. train.sh calls it automatically.
#
#   .venv/                   Python 3.11 venv made with uv (exact versions: constraints.txt)
#   openwakeword/            dscripka/openWakeWord at a pinned commit, patched with patches/*.patch,
#                            plus the melspectrogram/embedding feature models
#   piper-sample-generator/  rhasspy/piper-sample-generator at a pinned commit, plus the
#                            English and German Piper voices (~200 MB each)
#
# Needs git, curl, patch, internet and ~2.5 GB of disk. Tested on macOS (Apple Silicon);
# Linux should work but is untested.
set -euo pipefail
cd "$(dirname "$0")"
HERE=$(pwd -P)   # physical path (see the espeak-ng check below)

OWW_REPO=https://github.com/dscripka/openwakeword
OWW_COMMIT=368c03716d1e92591906a84949bc477f3a834455   # patches/*.patch are made against this commit
PSG_REPO=https://github.com/rhasspy/piper-sample-generator
PSG_COMMIT=2971426a55072f7d22fec416ca7800df8bd23207   # v3 package layout, which piper_shim/ expects
VOICES=https://github.com/rhasspy/piper-sample-generator/releases/download/v2.0.0
FEATURES=https://github.com/dscripka/openWakeWord/releases/download/v0.5.1
RES=openwakeword/openwakeword/resources/models

say() { printf '\n== %s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
sha256() { if command -v shasum >/dev/null; then shasum -a 256 "$1"; else sha256sum "$1"; fi | cut -d' ' -f1; }

# download URL DEST SHA256: skip if DEST is there and intact, otherwise fetch and verify.
download() {
  if [ -s "$2" ] && [ "$(sha256 "$2")" = "$3" ]; then return 0; fi
  echo "downloading $(basename "$2")"
  curl -fL --retry 3 --progress-bar -o "$2.part" "$1" || die "download failed: $1"
  [ "$(sha256 "$2.part")" = "$3" ] || die "checksum mismatch for $1"
  mv "$2.part" "$2"
}

# clone DIR REPO COMMIT
clone() {
  [ -d "$1/.git" ] || git clone -q "$2" "$1"
  if [ "$(git -C "$1" rev-parse HEAD)" != "$3" ]; then
    git -C "$1" checkout -q "$3" || die "cannot check out $3 in $1/ (delete the folder and re-run)"
  fi
}

for t in git curl patch; do command -v "$t" >/dev/null || die "'$t' is missing, please install it"; done
# espeak-ng (Piper's phonemizer) ignores a data path of 160+ characters and then fails, so
# $HERE/.venv/lib/python3.11/site-packages/piper/espeak-ng-data (HERE + 56) must stay short.
[ ${#HERE} -le 93 ] || die "the path of this folder is too long for espeak-ng (${#HERE} characters, at most 93).
Move the repository to a shorter path, e.g. ~/Echo_spot_second_life"
UV=$(command -v uv || true)
if [ -z "$UV" ] && [ -x "$HOME/.local/bin/uv" ]; then UV=$HOME/.local/bin/uv; fi   # default install dir
[ -n "$UV" ] || die "uv not found. Install it (https://docs.astral.sh/uv/), e.g.:
    curl -LsSf https://astral.sh/uv/install.sh | sh
then open a new terminal and run this script again."

say "Sources"
clone openwakeword "$OWW_REPO" "$OWW_COMMIT"
clone piper-sample-generator "$PSG_REPO" "$PSG_COMMIT"

say "Patching openwakeword"
# Upstream train.py has CRLF line endings; our patch is plain LF, so convert first (idempotent).
f=openwakeword/openwakeword/train.py
if grep -q $'\r' "$f"; then tr -d '\r' < "$f" > "$f.tmp" && mv "$f.tmp" "$f"; fi
for p in patches/*.patch; do
  if patch -d openwakeword -p1 -R --dry-run -f -s -i "$HERE/$p" >/dev/null 2>&1; then
    echo "already applied: $p"
  elif patch -d openwakeword -p1 -N --dry-run -s -i "$HERE/$p" >/dev/null 2>&1; then
    patch -d openwakeword -p1 -N -s -i "$HERE/$p"
    echo "applied: $p"
  else
    die "$p does not apply to openwakeword/ (local changes? delete the folder and re-run)"
  fi
done

say "Models"
mkdir -p "$RES" piper-sample-generator/models
download "$FEATURES/melspectrogram.onnx" "$RES/melspectrogram.onnx" \
  ba2b0e0f8b7b875369a2c89cb13360ff53bac436f2895cced9f479fa65eb176f
download "$FEATURES/embedding_model.onnx" "$RES/embedding_model.onnx" \
  70d164290c1d095d1d4ee149bc5e00543250a7316b59f31d056cff7bd3075c1f
download "$VOICES/en_US-libritts_r-medium.pt" piper-sample-generator/models/en_US-libritts_r-medium.pt \
  e95ee53770bf598c354a6e6dbfc95ccb259aeeb501d35a86be8a767429ab0ff6
download "$VOICES/de_DE-mls-medium.pt" piper-sample-generator/models/de_DE-mls-medium.pt \
  67c08d2f76a424b2e1a514e316c6fb5959cc35f168c0ddf6da7cecb7456f79c7

say "Python environment (.venv)"
[ -x .venv/bin/python ] || "$UV" venv -p 3.11 .venv
# constraints.txt fixes every version to the tested set. The pins that matter:
#   torch/torchaudio 2.1.2  torch-audiomentations 0.11 calls torchaudio.set_audio_backend(), gone in later torchaudio
#   numpy<2                 torch 2.1.2 (and the other pins) are built against NumPy 1.x
#   scipy 1.13.1            acoustics 0.2.6 imports scipy.special.sph_harm, removed from newer SciPy
#   pyarrow 15.0.2          newer pyarrow needs NumPy 2; datasets 2.14.6 also needs pa.PyExtensionType
#   setuptools<70           pronouncing needs pkg_resources (uv venvs have no setuptools); torch 2.1
#                           imports pkg_resources.packaging, which setuptools 70 removed
#   onnx 1.16.2             torch.onnx.export (model export at the end of training) needs onnx
#   datasets 2.14.6, speechbrain 0.5.14, audiomentations 0.33.0, torch-audiomentations 0.11.0, acoustics 0.2.6,
#   torchmetrics 1.2.0, torchinfo 1.8.0, mutagen 1.47.0, pronouncing 0.2.0, deep-phonemizer 0.0.19
#                           the versions openWakeWord's training notebook installs
#                           (its piper-phonemize is not needed: piper-tts below phonemizes)
pip_install() { "$UV" pip install --python .venv/bin/python -c constraints.txt "$@"; }
pip_install "numpy<2" "torch==2.1.2" "torchaudio==2.1.2" "scipy==1.13.1" "pyarrow==15.0.2" \
  "setuptools<70" "onnx==1.16.2" -e ./openwakeword \
  mutagen==1.47.0 torchinfo==1.8.0 torchmetrics==1.2.0 speechbrain==0.5.14 audiomentations==0.33.0 \
  torch-audiomentations==0.11.0 acoustics==0.2.6 pronouncing==0.2.0 datasets==2.14.6 deep-phonemizer==0.0.19 \
  webrtcvad-wheels pyyaml tqdm soundfile librosa
# piper-sample-generator (v3) imports piper-tts for PiperVoice and its espeak phonemizer. Its own
# requirements (numpy>=2, piper-tts 1.3) clash with the pins above, so it is used from the clone
# (piper_shim adds it to sys.path) and piper-tts is installed with --no-deps.
pip_install --no-deps "piper-tts==1.8.0"

say "Checking imports"
PYTHONWARNINGS=ignore .venv/bin/python -c "
import sys; sys.path.insert(0, 'piper_shim')
import torch, openwakeword, speechbrain, audiomentations, torch_audiomentations, acoustics, datasets, onnx
import generate_samples  # piper shim -> piper_sample_generator -> piper
print('imports OK: torch', torch.__version__, '| Apple GPU (MPS):', torch.backends.mps.is_available())
"
cat setup.sh constraints.txt patches/*.patch | cksum > .venv/.setup-complete
say "Setup complete"
