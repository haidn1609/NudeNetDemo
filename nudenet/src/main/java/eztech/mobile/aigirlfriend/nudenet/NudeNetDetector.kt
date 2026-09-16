package eztech.mobile.aigirlfriend.nudenet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer

/**
 * Phát hiện NSFW on-device (offline) bằng model NudeNet chạy trên ONNX Runtime.
 *
 * Module nudenet KHÔNG kèm model — app tự cung cấp qua 1 trong 3 cách:
 * ```
 * // 1) Nhúng trong assets của app (đóng gói vào APK) — hợp model nhỏ như 320n:
 * val detector = NudeNetDetector.fromAsset(ctx, "nudenet_320n.onnx", NudeNetModel.N320)
 *
 * // 2) Từ file trên máy (hợp model lớn như 640m):
 * val detector = NudeNetDetector.fromFile(File(ctx.filesDir, "models/nudenet_640m.onnx"), NudeNetModel.M640)
 *
 * // 3) Tải từ URL (blocking, tự cache vào filesDir/models):
 * val detector = NudeNetDetector.fromUrl(ctx, "https://.../640m.onnx", NudeNetModel.M640)
 *
 * val nsfw = detector.isNsfw(bitmap)
 * val boxes = detector.detect(bitmap) // chi tiết label + box để blur/che
 * detector.close()
 * ```
 * [detect]/[isNsfw] là blocking — gọi ở Dispatchers.Default/IO hoặc thread nền.
 */
class NudeNetDetector(
    private val session: OrtSession,
    private val inputSize: Int,
) : Closeable {

    /** Convenience: nạp trực tiếp từ đường dẫn file .onnx (ORT tự mmap, không nạp hết vào RAM). */
    constructor(modelPath: String, model: NudeNetModel = NudeNetModel.N320)
        : this(
            OrtEnvironment.getEnvironment().createSession(modelPath, OrtSession.SessionOptions()),
            model.inputSize,
        )

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val inputName: String = session.inputNames.first()

    /**
     * Trả về danh sách vùng phát hiện được (đã lọc theo [scoreThreshold] + NMS [iouThreshold]).
     * Blocking — hãy gọi trong Dispatchers.Default/IO.
     */
    fun detect(
        bitmap: Bitmap,
        scoreThreshold: Float = DEFAULT_SCORE_THRESHOLD,
        iouThreshold: Float = DEFAULT_IOU_THRESHOLD,
    ): List<Detection> {
        val (tensor, restore) = preprocess(bitmap)
        tensor.use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val raw = (result[0].value as Array<Array<FloatArray>>)[0] // [22][M]
                return postprocess(raw, restore, bitmap.width, bitmap.height, scoreThreshold, iouThreshold)
            }
        }
    }

    /**
     * true nếu ảnh có ít nhất một vùng thuộc [labels] với score ≥ [threshold].
     * Mặc định dùng nhóm [NudeLabels.NSFW_EXPOSED].
     */
    fun isNsfw(
        bitmap: Bitmap,
        labels: Set<String> = NudeLabels.NSFW_EXPOSED,
        threshold: Float = 0.5f,
    ): Boolean = detect(bitmap, scoreThreshold = threshold)
        .any { it.label in labels && it.score >= threshold }

    override fun close() {
        session.close()
    }

    // --- pre/post-processing ---

    /**
     * Bitmap -> tensor [1,3,dim,dim] RGB, /255, NCHW.
     * Letterbox: vẽ ảnh gốc (giữ tỉ lệ) vào góc trên-trái canvas vuông dim, pad đen ở phải/dưới
     * (khớp cv2.copyMakeBorder bottom/right của NudeNet).
     * Trả tensor + hệ số [restore] để map toạ độ model-space về ảnh gốc.
     */
    private fun preprocess(bitmap: Bitmap): Pair<OnnxTensor, Float> {
        val dim = inputSize
        val maxSide = maxOf(bitmap.width, bitmap.height)
        val scale = dim.toFloat() / maxSide // resize ảnh gốc -> model-space

        val square = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
        Canvas(square).apply {
            drawColor(Color.BLACK)
            drawBitmap(bitmap, null, RectF(0f, 0f, bitmap.width * scale, bitmap.height * scale), null)
        }

        val pixels = IntArray(dim * dim)
        square.getPixels(pixels, 0, dim, 0, 0, dim, dim)
        square.recycle()

        val area = dim * dim
        val chw = FloatArray(3 * area)
        for (i in 0 until area) {
            val p = pixels[i]
            chw[i] = ((p shr 16) and 0xFF) / 255f            // R plane
            chw[area + i] = ((p shr 8) and 0xFF) / 255f       // G plane
            chw[2 * area + i] = (p and 0xFF) / 255f           // B plane
        }

        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(chw),
            longArrayOf(1, 3, dim.toLong(), dim.toLong()),
        )
        return tensor to (1f / scale) // restore = maxSide/dim
    }

    /**
     * Decode YOLOv8: [raw] là [22][M] (4 box + 18 score). Lọc candidate ≥ 0.2,
     * đổi cx,cy,w,h -> x1,y1,x2,y2 (nhân [restore], clip vào ảnh), rồi NMS.
     */
    private fun postprocess(
        raw: Array<FloatArray>,
        restore: Float,
        imgW: Int,
        imgH: Int,
        scoreThreshold: Float,
        iouThreshold: Float,
    ): List<Detection> {
        val m = raw[0].size
        val boxes = ArrayList<RectF>()
        val scores = ArrayList<Float>()
        val labels = ArrayList<String>()

        for (n in 0 until m) {
            var bestIdx = -1
            var bestScore = 0f
            for (c in NudeLabels.LABELS.indices) {
                val s = raw[4 + c][n]
                if (s > bestScore) {
                    bestScore = s
                    bestIdx = c
                }
            }
            if (bestIdx < 0 || bestScore < CANDIDATE_THRESHOLD) continue

            val cx = raw[0][n]; val cy = raw[1][n]; val w = raw[2][n]; val h = raw[3][n]
            val x1 = ((cx - w / 2f) * restore).coerceIn(0f, imgW.toFloat())
            val y1 = ((cy - h / 2f) * restore).coerceIn(0f, imgH.toFloat())
            val x2 = ((cx + w / 2f) * restore).coerceIn(0f, imgW.toFloat())
            val y2 = ((cy + h / 2f) * restore).coerceIn(0f, imgH.toFloat())

            boxes.add(RectF(x1, y1, x2, y2))
            scores.add(bestScore)
            labels.add(NudeLabels.LABELS[bestIdx])
        }

        return nms(boxes, scores, scoreThreshold, iouThreshold)
            .map { Detection(labels[it], scores[it], boxes[it]) }
    }

    /** NMS global (mọi class chung), giống cv2.dnn.NMSBoxes của NudeNet. Trả index giữ lại. */
    private fun nms(
        boxes: List<RectF>,
        scores: List<Float>,
        scoreThreshold: Float,
        iouThreshold: Float,
    ): List<Int> {
        val order = scores.indices
            .filter { scores[it] >= scoreThreshold }
            .sortedByDescending { scores[it] }
        val removed = BooleanArray(scores.size)
        val keep = ArrayList<Int>()
        for (i in order) {
            if (removed[i]) continue
            keep.add(i)
            for (j in order) {
                if (j == i || removed[j]) continue
                if (iou(boxes[i], boxes[j]) > iouThreshold) removed[j] = true
            }
        }
        return keep
    }

    private fun iou(a: RectF, b: RectF): Float {
        val x1 = maxOf(a.left, b.left)
        val y1 = maxOf(a.top, b.top)
        val x2 = minOf(a.right, b.right)
        val y2 = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val areaA = maxOf(0f, a.width()) * maxOf(0f, a.height())
        val areaB = maxOf(0f, b.width()) * maxOf(0f, b.height())
        val union = areaA + areaB - inter
        return if (union <= 0f) 0f else inter / union
    }

    companion object {
        /** Ngưỡng lọc candidate trước NMS (khớp NudeNet = 0.2). */
        private const val CANDIDATE_THRESHOLD = 0.2f
        const val DEFAULT_SCORE_THRESHOLD = 0.25f
        const val DEFAULT_IOU_THRESHOLD = 0.45f

        /**
         * Nạp detector từ [source] (asset/file/url). Điểm vào thống nhất — điều phối sang
         * [fromAsset]/[fromFile]/[fromUrl] theo [ModelSource.type]. Blocking (URL có thể tải mạng)
         * — gọi ở thread nền.
         */
        fun from(context: Context, source: ModelSource): NudeNetDetector = when (source.type) {
            ModelSourceType.ASSET -> fromAsset(context, source.path, source.model)
            ModelSourceType.FILE -> fromFile(File(source.path), source.model)
            ModelSourceType.URL -> fromUrl(context, source.path, source.model)
        }

        /**
         * Nạp model nhúng trong assets của app (đóng gói vào APK). Đọc cả model vào RAM —
         * hợp model nhỏ (vd 320n ~12MB); model lớn nên dùng [fromFile]/[fromUrl].
         */
        fun fromAsset(
            context: Context,
            assetName: String = NudeNetModel.N320.fileName,
            model: NudeNetModel = NudeNetModel.N320,
        ): NudeNetDetector {
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            val session = OrtEnvironment.getEnvironment()
                .createSession(bytes, OrtSession.SessionOptions())
            return NudeNetDetector(session, model.inputSize)
        }

        /** Tạo detector từ [file] model .onnx trên máy. */
        fun fromFile(file: File, model: NudeNetModel = NudeNetModel.N320): NudeNetDetector =
            NudeNetDetector(file.absolutePath, model)

        /**
         * Tải model từ [url] về filesDir/models rồi tạo detector.
         * BLOCKING (gọi ở thread nền) — cần quyền INTERNET. File được cache theo [fileName]:
         * lần sau dùng lại, không tải lại.
         */
        fun fromUrl(
            context: Context,
            url: String,
            model: NudeNetModel = NudeNetModel.N320,
            fileName: String = model.fileName,
        ): NudeNetDetector = NudeNetDetector(downloadModel(context, url, fileName), model)

        /**
         * Tải file model nếu chưa có, trả về đường dẫn local. Ghi ra ".part" rồi rename
         * để không bao giờ để lại file tải dở coi như hợp lệ. Blocking.
         */
        fun downloadModel(context: Context, url: String, fileName: String): String {
            val dir = File(context.filesDir, "models").apply { mkdirs() }
            val out = File(dir, fileName)
            if (out.exists() && out.length() > 0L) return out.absolutePath // đã cache

            val tmp = File(dir, "$fileName.part")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "NudeNetDemo")
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} khi tải model từ $url")
                // URL trả trang HTML (login/redirect/404) thay vì file .onnx -> báo rõ, không lưu rác.
                if (conn.contentType?.contains("html", ignoreCase = true) == true) {
                    error("URL trả về HTML chứ không phải model .onnx — có thể sai URL hoặc cần đăng nhập: $url")
                }
                conn.inputStream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
                // Kiểm magic: ONNX (protobuf) không phải HTML. Bắt cả khi content-type khai sai.
                val head = ByteArray(64)
                val n = tmp.inputStream().use { it.read(head) }
                if (String(head, 0, maxOf(0, n)).contains("<!DOCTYPE", true) ||
                    String(head, 0, maxOf(0, n)).contains("<html", true)
                ) {
                    error("Nội dung tải về là HTML, không phải model .onnx hợp lệ: $url")
                }
                check(tmp.renameTo(out)) { "Không thể lưu model đã tải" }
                return out.absolutePath
            } catch (e: Exception) {
                tmp.delete()
                throw e
            } finally {
                conn.disconnect()
            }
        }
    }
}
