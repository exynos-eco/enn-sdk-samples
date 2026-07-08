// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageediting.data

object ModelConstants {
    const val MODEL_NAME = "LaMa_Dilated.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val OUTPUT_DATA_TYPE = DataType.FLOAT32

    const val INPUT1_N = 1
    const val INPUT1_C = 3
    const val INPUT1_H = 512
    const val INPUT1_W = 512
    const val INPUT1_ELEMENT_COUNT = INPUT1_N * INPUT1_C * INPUT1_H * INPUT1_W
    const val INPUT1_BYTE_SIZE = INPUT1_ELEMENT_COUNT * 4

    const val INPUT2_N = 1
    const val INPUT2_C = 1
    const val INPUT2_H = 512
    const val INPUT2_W = 512
    const val INPUT2_ELEMENT_COUNT = INPUT2_N * INPUT2_C * INPUT2_H * INPUT2_W
    const val INPUT2_BYTE_SIZE = INPUT2_ELEMENT_COUNT * 4

    const val OUTPUT_N = 1
    const val OUTPUT_C = 3
    const val OUTPUT_H = 512
    const val OUTPUT_W = 512
    const val OUTPUT_ELEMENT_COUNT = OUTPUT_N * OUTPUT_C * OUTPUT_H * OUTPUT_W
    const val OUTPUT_BYTE_SIZE = OUTPUT_ELEMENT_COUNT * 4

    const val PREVIEW_W = 512
    const val PREVIEW_H = 512
}
