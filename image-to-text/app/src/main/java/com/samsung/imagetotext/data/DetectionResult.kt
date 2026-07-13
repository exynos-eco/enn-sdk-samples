// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imagetotext.data

import android.graphics.RectF

data class DetectionResult(
    var score: Pair<String, Float>,
    val boundingBox: RectF? = null
) {
    fun requireBoundingBox(): RectF {
        return boundingBox!!
    }
}