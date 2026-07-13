package com.samsung.imagetotext.data

object ModelConstants {
    const val DETECTOR_MODEL_NAME = "EasyOCR_Decoder.nnc"
    const val RECOGNIZER_MODEL_NAME = "EasyOCR_Recognizer.nnc"
    const val CHARACTER_FILE_NAME = "easyocr_character.txt"

    const val DETECTOR_INPUT_WIDTH = 768
    const val DETECTOR_INPUT_HEIGHT = 768
    const val DETECTOR_INPUT_CHANNELS = 3
    const val DETECTOR_OUTPUT_WIDTH = 384
    const val DETECTOR_OUTPUT_HEIGHT = 384
    const val DETECTOR_OUTPUT_CHANNELS = 2

    const val RECOGNIZER_INPUT_WIDTH = 100
    const val RECOGNIZER_INPUT_HEIGHT = 32
    const val RECOGNIZER_INPUT_CHANNELS = 1
    const val RECOGNIZER_TIME_STEPS = 24
    const val RECOGNIZER_CLASS_COUNT = 6719

    val DETECTOR_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    val DETECTOR_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    const val DEFAULT_TEXT_THRESHOLD = 0.70f
    const val DEFAULT_LINK_THRESHOLD = 0.40f
    const val DEFAULT_LOW_TEXT_THRESHOLD = 0.40f
    const val DEFAULT_RECOGNITION_THRESHOLD = 0.10f

    const val MIN_COMPONENT_AREA = 10
    const val MAX_TEXT_BOXES = 30
}
