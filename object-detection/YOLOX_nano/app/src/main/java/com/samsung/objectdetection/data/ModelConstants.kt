package com.samsung.objectdetection.data

object ModelConstants {
    const val MODEL_NAME = "YOLOX_nano.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 416
    const val INPUT_SIZE_H = 416
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 1.0F / 255.0F
    const val INPUT_CONVERSION_OFFSET = 0.0F

    val OUTPUT_DATA_TYPE = DataType.FLOAT32

    const val OUTPUT_NUM_BOXES = 3549
    const val OUTPUT_NUM_CLASSES = 80
    const val OUTPUT_BOX_COORDS = 4

    const val LABEL_FILE = "YOLOX_nano.txt"
}