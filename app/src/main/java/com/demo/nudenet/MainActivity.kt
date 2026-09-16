package com.demo.nudenet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import eztech.mobile.aigirlfriend.nudenet.Detection
import eztech.mobile.aigirlfriend.nudenet.ModelSource
import eztech.mobile.aigirlfriend.nudenet.ModelSourceType
import eztech.mobile.aigirlfriend.nudenet.NudeNetModel

class MainActivity : AppCompatActivity() {

    private lateinit var img: ImageView
    private lateinit var tvVerdict: TextView
    private lateinit var tvResult: TextView
    private lateinit var progress: ProgressBar

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { runDetection(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        img = findViewById(R.id.imgPreview)
        tvVerdict = findViewById(R.id.tvVerdict)
        tvResult = findViewById(R.id.tvResult)
        progress = findViewById(R.id.progress)
        findViewById<Button>(R.id.btnPick).setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    private fun selectedModel(): NudeNetModel =
        if (findViewById<RadioButton>(R.id.rb640).isChecked) NudeNetModel.M640 else NudeNetModel.N320

    /** Nguồn model: 320n nhúng APK; 640m đọc file ở filesDir/models (đổi .url(...) nếu muốn tải mạng). */
    private fun modelSource(model: NudeNetModel): ModelSource = when (model) {
        NudeNetModel.N320 -> ModelSource(model, ModelSourceType.ASSET, model.fileName)
        NudeNetModel.M640 -> ModelSource(
            model, ModelSourceType.URL,
           "https://github.com/notAI-tech/NudeNet/releases/download/v3.4-weights/640m.onnx"
        )
    }

    private fun runDetection(uri: Uri) {
        val bitmap = decodeBitmap(uri) ?: run {
            Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show()
            return
        }
        val model = selectedModel()
        progress.visibility = ProgressBar.VISIBLE
        tvVerdict.text = ""
        tvResult.text = ""
        img.setImageBitmap(bitmap)

        Thread {
            try {
                val result = NudeNetHelper.analyze(this, bitmap, modelSource(model))
                val drawn = drawBoxes(bitmap, result.detections)

                runOnUiThread {
                    progress.visibility = ProgressBar.GONE
                    img.setImageBitmap(drawn)
                    tvVerdict.text = if (result.nsfw) "NSFW ⚠️" else "SAFE ✅"
                    tvVerdict.setTextColor(if (result.nsfw) Color.RED else Color.rgb(0, 150, 0))
                    tvResult.text = buildString {
                        append("Model ${model.name}  •  ${result.elapsedMs}ms  •  ${result.detections.size} vùng\n\n")
                        if (result.detections.isEmpty()) {
                            append("(không phát hiện gì)")
                        } else {
                            result.detections.sortedByDescending { it.score }.forEach {
                                append("• ${it.label}  ${(it.score * 100).toInt()}%\n")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = ProgressBar.GONE
                    tvResult.text = "Lỗi: ${e.message}"
                }
            }
        }.start()
    }

    /** Decode ảnh từ uri, tự downsample nếu cạnh > 1600px để tránh OOM. */
    private fun decodeBitmap(uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }

    private fun drawBoxes(src: Bitmap, results: List<Detection>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(3f, out.width / 200f)
            color = Color.RED
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = maxOf(24f, out.width / 40f)
            isAntiAlias = true
        }
        val bgPaint = Paint().apply { color = Color.argb(180, 0, 0, 0) }

        results.forEach { d ->
            canvas.drawRect(d.box, boxPaint)
            val label = "${d.label} ${(d.score * 100).toInt()}%"
            val tw = textPaint.measureText(label)
            val th = textPaint.textSize + 6f
            val ty = if (d.box.top - th < 0) d.box.top + th else d.box.top
            canvas.drawRect(d.box.left, ty - th, d.box.left + tw + 8f, ty, bgPaint)
            canvas.drawText(label, d.box.left + 4f, ty - 6f, textPaint)
        }
        return out
    }
}
