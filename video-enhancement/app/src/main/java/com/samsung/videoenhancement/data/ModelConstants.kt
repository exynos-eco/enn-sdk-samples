package com.samsung.videoenhancement.data

object ModelConstants {
    const val MODEL_NAME = "RIFE.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 448
    const val INPUT_SIZE_H = 256
    const val INPUT_SIZE_C = 3
    const val INPUT_COUNT = 2

    const val INPUT_CONVERSION_SCALE = 1.0F / 255.0F
    const val INPUT_CONVERSION_OFFSET = 0.0F

    val OUTPUT_DATA_TYPE = DataType.FLOAT32
    const val OUTPUT_SIZE_W = 448
    const val OUTPUT_SIZE_H = 256
    const val OUTPUT_SIZE_C = 3
}
