// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.depthestimation.data

object ModelConstants {
    const val MODEL_NAME = "MegaDepth.nnc"

    const val OUTPUT_INVERT = true
    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 512
    const val INPUT_SIZE_H = 384
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 255F
    const val INPUT_CONVERSION_OFFSET = 0F

    val OUTPUT_DATA_TYPE = DataType.FLOAT32

    const val OUTPUT_SIZE_W = 512
    const val OUTPUT_SIZE_H = 384
}
