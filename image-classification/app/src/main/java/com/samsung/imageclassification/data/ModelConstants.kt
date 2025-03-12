// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageclassification.data

object ModelConstants {
    const val MODEL_NAME = "densenet121.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 224
    const val INPUT_SIZE_H = 224
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 1 / 255F
    const val INPUT_CONVERSION_OFFSET = 0F

    val OUTPUT_DATA_TYPE = DataType.FLOAT32

    const val OUTPUT_CONVERSION_SCALE = 1F
    const val OUTPUT_CONVERSION_OFFSET = 0F

    const val LABEL_FILE = "densenet121.txt"
}