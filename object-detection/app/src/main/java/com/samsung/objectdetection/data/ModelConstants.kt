// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.objectdetection.data

object ModelConstants {
    const val MODEL_NAME = "yolov5s-new.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.HWC

    const val INPUT_SIZE_W = 640
    const val INPUT_SIZE_H = 640
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 127.5F
    const val INPUT_CONVERSION_OFFSET = 127.5F

    val OUTPUT_DATA_TYPE = DataType.FLOAT32

    const val OUTPUT_SIZE_W = 25200
    const val OUTPUT_SIZE_H = 85

    const val LABEL_FILE = "coco.txt"
}