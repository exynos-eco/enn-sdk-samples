// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.driveassistance.data

object ModelConstants {
    const val MODEL_NAME = "YoloP_320x320_simplify_O2_SingleCore.nnc"
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 320
    const val INPUT_SIZE_H = 320
    const val INPUT_SIZE_C = 3
    const val INPUT_ELEMENT_COUNT = INPUT_SIZE_C * INPUT_SIZE_H * INPUT_SIZE_W
}
