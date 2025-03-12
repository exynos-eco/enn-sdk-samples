// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.poseestimation.data

import android.graphics.PointF


enum class BodyPart(val position: Int) {
    NOSE(0),
    LEFT_EYE(1),
    RIGHT_EYE(2),
    LEFT_EAR(3),
    RIGHT_EAR(4),
    LEFT_SHOULDER(5),
    RIGHT_SHOULDER(6),
    LEFT_ELBOW(7),
    RIGHT_ELBOW(8),
    LEFT_WRIST(9),
    RIGHT_WRIST(10),
    LEFT_HIP(11),
    RIGHT_HIP(12),
    LEFT_KNEE(13),
    RIGHT_KNEE(14),
    LEFT_ANKLE(15),
    RIGHT_ANKLE(16);

    companion object {
        private val map = values().associateBy(BodyPart::position)
        fun fromInt(position: Int): BodyPart = map.getValue(position)
    }
}


data class Human(
    var NOSE: KeyPoint,
    val LEFT_EYE: KeyPoint,
    var RIGHT_EYE: KeyPoint,
    var LEFT_EAR: KeyPoint,
    var RIGHT_EAR: KeyPoint,
    var LEFT_SHOULDER: KeyPoint,
    var RIGHT_SHOULDER: KeyPoint,
    var LEFT_ELBOW: KeyPoint,
    var RIGHT_ELBOW: KeyPoint,
    var LEFT_WRIST: KeyPoint,
    var RIGHT_WRIST: KeyPoint,
    var LEFT_HIP: KeyPoint,
    var RIGHT_HIP: KeyPoint,
    var LEFT_KNEE: KeyPoint,
    var RIGHT_KNEE: KeyPoint,
    var LEFT_ANKLE: KeyPoint,
    var RIGHT_ANKLE: KeyPoint,
    var score: Float = 0F,
) {
    companion object {
        fun fromOutput(output: Array<FloatArray>): Human {
            val keyPoints = mutableListOf<KeyPoint>()
            var totalScore = 0F

            for (point in output) {
                val (x, y, score) = point
                keyPoints.add(KeyPoint(PointF(x, y), score))
                totalScore += score
            }

            return Human(
                NOSE = keyPoints[0],
                LEFT_EYE = keyPoints[1],
                RIGHT_EYE = keyPoints[2],
                LEFT_EAR = keyPoints[3],
                RIGHT_EAR = keyPoints[4],
                LEFT_SHOULDER = keyPoints[5],
                RIGHT_SHOULDER = keyPoints[6],
                LEFT_ELBOW = keyPoints[7],
                RIGHT_ELBOW = keyPoints[8],
                LEFT_WRIST = keyPoints[9],
                RIGHT_WRIST = keyPoints[10],
                LEFT_HIP = keyPoints[11],
                RIGHT_HIP = keyPoints[12],
                LEFT_KNEE = keyPoints[13],
                RIGHT_KNEE = keyPoints[14],
                LEFT_ANKLE = keyPoints[15],
                RIGHT_ANKLE = keyPoints[16],
                score = totalScore / 17
            )
        }
    }

    var points = listOf(
        NOSE,
        LEFT_EYE,
        RIGHT_EYE,
        LEFT_EAR,
        RIGHT_EAR,
        LEFT_SHOULDER,
        RIGHT_SHOULDER,
        LEFT_ELBOW,
        RIGHT_ELBOW,
        LEFT_WRIST,
        RIGHT_WRIST,
        LEFT_HIP,
        RIGHT_HIP,
        LEFT_KNEE,
        RIGHT_KNEE,
        LEFT_ANKLE,
        RIGHT_ANKLE
    )

    var edges = listOf(
        Pair(NOSE, LEFT_EYE),
        Pair(LEFT_EYE, LEFT_EAR),
        Pair(NOSE, RIGHT_EYE),
        Pair(RIGHT_EYE, RIGHT_EAR),

        Pair(RIGHT_SHOULDER, LEFT_SHOULDER),

        Pair(LEFT_SHOULDER, LEFT_ELBOW),
        Pair(LEFT_ELBOW, LEFT_WRIST),
        Pair(RIGHT_SHOULDER, RIGHT_ELBOW),
        Pair(RIGHT_ELBOW, RIGHT_WRIST),

        Pair(LEFT_SHOULDER, LEFT_HIP),
        Pair(RIGHT_SHOULDER, RIGHT_HIP),

        Pair(LEFT_HIP, RIGHT_HIP),

        Pair(LEFT_HIP, LEFT_KNEE),
        Pair(LEFT_KNEE, LEFT_ANKLE),

        Pair(RIGHT_HIP, RIGHT_KNEE),
        Pair(RIGHT_KNEE, RIGHT_ANKLE)
    )
}