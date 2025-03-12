// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.poseestimation.data

object ModelConstants {
    const val MODEL_NAME = "float32_pose.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.HWC

    const val INPUT_SIZE_W = 257
    const val INPUT_SIZE_H = 257
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 127.5F
    const val INPUT_CONVERSION_OFFSET = 127.5F

    val HEATMAP_DATA_TYPE = DataType.FLOAT32

    const val HEATMAP_SIZE_W = 9
    const val HEATMAP_SIZE_H = 9
    const val HEATMAP_SIZE_C = 17

    val OFFSET_DATA_TYPE = DataType.FLOAT32

    const val OFFSET_SIZE_W = 9
    const val OFFSET_SIZE_H = 9
    const val OFFSET_SIZE_C = 34
}