# Cookbook for zipvoice-infer  

## Try using optimized model

``` shell
python3 ./python-api-examples/offline-zeroshot-tts.py \
  --zipvoice-flow-matching-model fm_decoder_int8_opt.onnx \
  --zipvoice-text-model sherpa-onnx-zipvoice-distill-zh-en-emilia/text_encoder_int8.onnx \
  --zipvoice-data-dir sherpa-onnx-zipvoice-distill-zh-en-emilia/espeak-ng-data \
  --zipvoice-pinyin-dict sherpa-onnx-zipvoice-distill-zh-en-emilia/pinyin.raw \
  --zipvoice-tokens sherpa-onnx-zipvoice-distill-zh-en-emilia/tokens.txt \
  --zipvoice-vocoder sherpa-onnx-zipvoice-distill-zh-en-emilia/vocos_24khz.onnx \
  --prompt-audio prompt.wav \
  --zipvoice-num-steps 4 \
  --num-threads 4 \
  --prompt-text "你就需要我这种专业人士的帮助，就像手无缚鸡之力的人进入雪山狩猎，一定需要最老练的猎人指导。" \
  --output-filename gen_opti_external.onnx \
  "使用新一代卡尔迪的语音合成引擎"
  
```

## Generate subtitles

```shell
./python-api-examples/generate-subtitles.py  \
    --ten-vad-model ten-vad.onnx \
    --tokens sherpa-onnx-paraformer-zh-int8-2025-10-07/tokens.txt \
    --paraformer sherpa-onnx-paraformer-zh-int8-2025-10-07/model.int8.onnx \
    --num-threads=2 \
    --decoding-method=greedy_search \
    --debug=false \
    --sample-rate=16000 \
    --feature-dim=80 \
    *.mp4
```