// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.gazeestimation.data

object ModelConstants {
    const val MODEL_NAME = "Gaze_MobileNetV2.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 448
    const val INPUT_SIZE_H = 448
    const val INPUT_SIZE_C = 3

    const val NUM_BINS = 90

    const val PITCH_INDEX_TO_DEG_SCALE = 4f
    const val PITCH_INDEX_TO_DEG_OFFSET = -180f

    const val YAW_INDEX_TO_DEG_SCALE = 4f
    const val YAW_INDEX_TO_DEG_OFFSET = -180f
}