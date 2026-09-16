package eztech.mobile.aigirlfriend.nudenet

import android.graphics.RectF

/**
 * Một vùng phát hiện được: [label] (một trong [NudeLabels.LABELS]), [score] 0..1,
 * [box] toạ độ pixel theo ảnh gốc (left, top, right, bottom).
 */
data class Detection(
    val label: String,
    val score: Float,
    val box: RectF,
)
