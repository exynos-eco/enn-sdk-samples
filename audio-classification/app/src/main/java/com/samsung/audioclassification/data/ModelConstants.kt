// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.audioclassification.data

object ModelConstants {
    const val MODEL_NAME = "YamNet.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.CHW

    // YAMNet input shape: [1, C, H, W] = [1, 1, 96, 64]
    const val INPUT_SIZE_W = 64          // W: Mel frequency bins (NUM_MEL_BINS)
    const val INPUT_SIZE_H = 96          // H: Time frames, 0.96s of audio (NUM_FRAMES)
    const val INPUT_SIZE_C = 1           // C: Single channel (mono audio)

    // YAMNet audio parameters
    const val SAMPLE_RATE = 16000        // 16kHz
    const val STFT_WINDOW_SIZE = 400     // 25ms window @ 16kHz
    const val STFT_HOP_SIZE = 160        // 10ms hop @ 16kHz
    const val NUM_CLASSES = 521          // Number of sound classes

    const val INPUT_CONVERSION_SCALE = 1F
    const val INPUT_CONVERSION_OFFSET = 0F

    val OUTPUT_DATA_TYPE = DataType.FLOAT32

    const val OUTPUT_CONVERSION_SCALE = 1F
    const val OUTPUT_CONVERSION_OFFSET = 0F

    const val LABEL_FILE = "yamnet_labels.txt"
}