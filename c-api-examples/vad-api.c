// c-api-examples/vad-api.c
//
// Copyright (c)  2024  Xiaomi Corporation
//
// This file demonstrates how to use VAD (Voice Activity Detection) with
// sherpa-onnx's C API. It detects speech segments in an input WAV file and
// displays their timestamps.
//
// clang-format off
//
// To use silero-vad:
//  wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
//
// To use ten-vad:
//  wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/ten-vad.onnx
//
// Example usage:
//  ./vad-api silero_vad.onnx input.wav
//  ./vad-api ten-vad.onnx input.wav
//
// clang-format on

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "sherpa-onnx/c-api/c-api.h"

int32_t main(int32_t argc, const char *argv[]) {
  if (argc < 3) {
    fprintf(stderr, "Usage: %s <vad_model> <wav_file>\n", argv[0]);
    fprintf(stderr, "Example:\n");
    fprintf(stderr, "  %s silero_vad.onnx input.wav\n", argv[0]);
    fprintf(stderr, "  %s ten-vad.onnx input.wav\n", argv[0]);
    return -1;
  }

  const char *vad_model = argv[1];
  const char *wav_filename = argv[2];

  // Check if files exist
  if (!SherpaOnnxFileExists(vad_model)) {
    fprintf(stderr, "Error: VAD model '%s' does not exist\n", vad_model);
    return -1;
  }

  if (!SherpaOnnxFileExists(wav_filename)) {
    fprintf(stderr, "Error: WAV file '%s' does not exist\n", wav_filename);
    return -1;
  }

  // Determine VAD type from model filename
  int32_t use_silero_vad = 0;
  int32_t use_ten_vad = 0;

  if (strstr(vad_model, "silero") != NULL) {
    printf("Using Silero VAD\n");
    use_silero_vad = 1;
  } else if (strstr(vad_model, "ten-vad") != NULL ||
             strstr(vad_model, "ten_vad") != NULL) {
    printf("Using Ten-VAD\n");
    use_ten_vad = 1;
  } else {
    fprintf(stderr,
            "Error: Unknown VAD model type. Expected silero_vad.onnx or "
            "ten-vad.onnx\n");
    return -1;
  }

  // Load WAV file
  printf("Loading WAV file: %s\n", wav_filename);
  const SherpaOnnxWave *wave = SherpaOnnxReadWave(wav_filename);
  if (wave == NULL) {
    fprintf(stderr, "Failed to read WAV file: %s\n", wav_filename);
    return -1;
  }

  printf("Sample rate: %d Hz\n", wave->sample_rate);
  printf("Number of samples: %d\n", wave->num_samples);
  printf("Duration: %.2f seconds\n",
         (float)wave->num_samples / wave->sample_rate);

  // Check sample rate (VAD works best at 16kHz)
  if (wave->sample_rate != 16000) {
    fprintf(stderr, "Warning: Expected sample rate 16000 Hz, got %d Hz\n",
            wave->sample_rate);
    fprintf(stderr,
            "Note: The VAD may not work optimally at this sample rate\n");
  }

  // Configure VAD
  SherpaOnnxVadModelConfig vad_config;
  memset(&vad_config, 0, sizeof(vad_config));

  if (use_silero_vad) {
    vad_config.silero_vad.model = vad_model;
    vad_config.silero_vad.threshold = 0.5;
    vad_config.silero_vad.min_silence_duration = 0.25;
    vad_config.silero_vad.min_speech_duration = 0.25;
    vad_config.silero_vad.max_speech_duration = 10;
    vad_config.silero_vad.window_size = 512;
  } else if (use_ten_vad) {
    vad_config.ten_vad.model = vad_model;
    vad_config.ten_vad.threshold = 0.5;
    vad_config.ten_vad.min_silence_duration = 0.25;
    vad_config.ten_vad.min_speech_duration = 0.25;
    vad_config.ten_vad.max_speech_duration = 10;
    vad_config.ten_vad.window_size = 256;
  }

  vad_config.sample_rate = wave->sample_rate;
  vad_config.num_threads = 1;
  vad_config.debug = 0;

  // Create VAD detector
  printf("\nInitializing VAD detector...\n");
  const SherpaOnnxVoiceActivityDetector *vad =
      SherpaOnnxCreateVoiceActivityDetector(&vad_config, 30);

  if (vad == NULL) {
    fprintf(stderr, "Failed to create VAD detector\n");
    SherpaOnnxFreeWave(wave);
    return -1;
  }

  // Get window size based on VAD type
  int32_t window_size;
  if (use_silero_vad) {
    window_size = vad_config.silero_vad.window_size;
  } else {
    window_size = vad_config.ten_vad.window_size;
  }

  printf("Window size: %d samples\n", window_size);
  printf("\nDetecting speech segments...\n");
  printf("---------------------------------------\n");

  // Process audio
  int32_t num_segments = 0;
  float total_speech_duration = 0.0f;
  int32_t i = 0;

  while (i < wave->num_samples) {
    int32_t chunk_size = (i + window_size <= wave->num_samples)
                             ? window_size
                             : (wave->num_samples - i);

    if (i + window_size < wave->num_samples) {
      SherpaOnnxVoiceActivityDetectorAcceptWaveform(vad, wave->samples + i,
                                                    window_size);
    } else {
      // Flush remaining samples
      SherpaOnnxVoiceActivityDetectorAcceptWaveform(vad, wave->samples + i,
                                                    chunk_size);
      SherpaOnnxVoiceActivityDetectorFlush(vad);
    }

    // Process detected speech segments
    while (!SherpaOnnxVoiceActivityDetectorEmpty(vad)) {
      const SherpaOnnxSpeechSegment *segment =
          SherpaOnnxVoiceActivityDetectorFront(vad);

      float start_time = segment->start / (float)wave->sample_rate;
      float duration = segment->n / (float)wave->sample_rate;
      float end_time = start_time + duration;

      num_segments++;
      total_speech_duration += duration;

      printf("Segment %d:\n", num_segments);
      printf("  Start:    %.3f s\n", start_time);
      printf("  End:      %.3f s\n", end_time);
      printf("  Duration: %.3f s\n", duration);
      printf("  Samples:  %d\n", segment->n);
      printf("\n");

      SherpaOnnxDestroySpeechSegment(segment);
      SherpaOnnxVoiceActivityDetectorPop(vad);
    }

    i += window_size;
  }

  printf("---------------------------------------\n");
  printf("\nSummary:\n");
  printf("  Total segments detected: %d\n", num_segments);
  printf("  Total speech duration: %.2f s\n", total_speech_duration);
  printf("  Total file duration: %.2f s\n",
         (float)wave->num_samples / wave->sample_rate);
  printf(
      "  Speech ratio: %.1f%%\n",
      (total_speech_duration / ((float)wave->num_samples / wave->sample_rate)) *
          100);

  // Cleanup
  SherpaOnnxDestroyVoiceActivityDetector(vad);
  SherpaOnnxFreeWave(wave);

  printf("\nDone!\n");
  return 0;
}
