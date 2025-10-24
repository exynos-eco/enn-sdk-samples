// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.poseestimation.data

object ModelConstants {
    const val MODEL_NAME = "HRNet_Pose.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 192
    const val INPUT_SIZE_H = 256
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 127.5F
    const val INPUT_CONVERSION_OFFSET = 127.5F

    val OUTPUT_DATA_TYPE = DataType.FLOAT32
    const val OUTPUT_SIZE_W = 48
    const val OUTPUT_SIZE_H = 64
    const val OUTPUT_SIZE_C = 17
}