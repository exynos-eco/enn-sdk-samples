// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageenhance.data

object ModelConstants {
    const val MODEL_NAME = "real_esrgan_general_x4v3.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 128
    const val INPUT_SIZE_H = 128
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 256F
    const val INPUT_CONVERSION_OFFSET = 0F

    val OUTPUT_DATA_TYPE = DataType.FLOAT32
    val OUTPUT_DATA_LAYER = LayerType.CHW

    const val OUTPUT_SIZE_W = 512
    const val OUTPUT_SIZE_H = 512
    const val OUTPUT_SIZE_C = INPUT_SIZE_C

    const val OUTPUT_CONVERSION_SCALE = 256F
    const val OUTPUT_CONVERSION_OFFSET = 0F
}