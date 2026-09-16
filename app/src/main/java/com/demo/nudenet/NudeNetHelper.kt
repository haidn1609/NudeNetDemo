package com.demo.nudenet

import android.content.Context
import android.graphics.Bitmap
import eztech.mobile.aigirlfriend.nudenet.Detection
import eztech.mobile.aigirlfriend.nudenet.ModelSource
import eztech.mobile.aigirlfriend.nudenet.NudeLabels
import eztech.mobile.aigirlfriend.nudenet.NudeNetDetector

/** Kết quả một lần phân tích ảnh. */
data class NsfwResult(
    val nsfw: Boolean,
    val detections: List<Detection>,
    val elapsedMs: Long,
)

/**
 * Gói việc nạp model NudeNet + chạy detect, tách khỏi UI.
 * Nguồn model do caller mô tả bằng [ModelSource] và truyền thẳng vào [analyze]/[createDetector].
 * BLOCKING (URL có thể tải mạng) — gọi ở thread nền / Dispatchers.IO.
 */
object NudeNetHelper {

    /** Nạp detector từ [source]. Caller tự close() (hoặc dùng [analyze] để tự đóng). */
    fun createDetector(context: Context, source: ModelSource): NudeNetDetector =
        NudeNetDetector.from(context, source)

    /**
     * Phân tích [bitmap] với model ở [source]: nạp, detect, tính verdict NSFW rồi tự đóng detector.
     * Blocking — gọi ở thread nền.
     */
    fun analyze(
        context: Context,
        bitmap: Bitmap,
        source: ModelSource,
        nsfwThreshold: Float = 0.5f,
    ): NsfwResult = createDetector(context, source).use { detector ->
        val t0 = System.currentTimeMillis()
        val detections = detector.detect(bitmap)
        val elapsed = System.currentTimeMillis() - t0
        val nsfw = detections.any { it.label in NudeLabels.NSFW_EXPOSED && it.score >= nsfwThreshold }
        NsfwResult(nsfw, detections, elapsed)
    }
}
