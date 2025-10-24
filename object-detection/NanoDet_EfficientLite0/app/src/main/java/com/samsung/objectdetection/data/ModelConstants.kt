package com.samsung.objectdetection.data

object ModelConstants {
    const val MODEL_NAME = "NanoDet_EfficientLite0.nnc"

    val INPUT_DATA_TYPE = DataType.FLOAT32
    val INPUT_DATA_LAYER = LayerType.CHW

    const val INPUT_SIZE_W = 320
    const val INPUT_SIZE_H = 320
    const val INPUT_SIZE_C = 3

    const val INPUT_CONVERSION_SCALE = 1.0F / 255.0F
    val NORM_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    val NORM_STD  = floatArrayOf(0.229f, 0.224f, 0.225f)

    val OUTPUT_DATA_TYPE = DataType.FLOAT32

    const val OUTPUT_NUM_CLASSES = 80
    const val REG_MAX = 7
    val STRIDES = intArrayOf(8, 16, 32)
    const val KEEP_RATIO = true

    const val LABEL_FILE = "NanoDet_EfficientLite0.txt"
}
