// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.poseestimation.data

object ModelConstants {

    const val MODEL_NAME = "PoseNet_MobileNet.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 257
    const val INPUT_SIZE_H = 513
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 127.5F
    const val INPUT_CONVERSION_OFFSET = 127.5F

    val HEATMAP_DATA_TYPE = DataType.FLOAT32
    const val HEATMAP_SIZE_W = 17
    const val HEATMAP_SIZE_H = 33
    const val HEATMAP_SIZE_C = 17

    val OFFSET_DATA_TYPE = DataType.FLOAT32

    const val OFFSET_SIZE_W = 17
    const val OFFSET_SIZE_H = 33
    const val OFFSET_SIZE_C = 34
}