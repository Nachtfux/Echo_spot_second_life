#!/bin/bash
# Local openWakeWord training environment on macOS (Apple Silicon), Python 3.11 via uv.
set -x
cd "$(dirname "$0")"
UV=/Users/loki-e/.local/bin/uv
$UV venv -p 3.11 .venv || exit 1
. .venv/bin/activate
$UV pip install "numpy<2" torch torchaudio || exit 1
$UV pip install piper-phonemize-cross webrtcvad-wheels || exit 1
[ -d piper-sample-generator ] || git clone -q https://github.com/rhasspy/piper-sample-generator
[ -s piper-sample-generator/models/en_US-libritts_r-medium.pt ] || curl -sSL -o piper-sample-generator/models/en_US-libritts_r-medium.pt https://github.com/rhasspy/piper-sample-generator/releases/download/v2.0.0/en_US-libritts_r-medium.pt
[ -d openwakeword ] || git clone -q https://github.com/dscripka/openwakeword
$UV pip install -e ./openwakeword || exit 1
$UV pip install mutagen==1.47.0 torchinfo==1.8.0 torchmetrics==1.2.0 speechbrain==0.5.14 audiomentations==0.33.0 \
  torch-audiomentations==0.11.0 acoustics==0.2.6 pronouncing==0.2.0 datasets==2.14.6 deep-phonemizer==0.0.19 \
  pyyaml tqdm scipy soundfile librosa || exit 1
mkdir -p openwakeword/openwakeword/resources/models
for f in embedding_model.onnx melspectrogram.onnx; do
  [ -s openwakeword/openwakeword/resources/models/$f ] || curl -sSL -o openwakeword/openwakeword/resources/models/$f https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/$f
done
python -c "import torch, piper_phonemize, openwakeword, speechbrain, audiomentations, torch_audiomentations, acoustics, datasets; print('IMPORTS OK', torch.__version__, 'mps', torch.backends.mps.is_available())"
echo SETUP_DONE
