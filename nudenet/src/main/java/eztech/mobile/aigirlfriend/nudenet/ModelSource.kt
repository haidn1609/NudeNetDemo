package eztech.mobile.aigirlfriend.nudenet

/** Nguồn nạp model: nhúng trong assets, file trên máy, hoặc tải từ URL. */
enum class ModelSourceType { ASSET, FILE, URL }

/**
 * Mô tả một model để nạp — MỌI field bắt buộc, không có giá trị mặc định:
 *  - [model]: 320n hay 640m (quyết định inputSize).
 *  - [type]: nguồn (ASSET / FILE / URL).
 *  - [path]: giá trị theo [type] — tên asset (ASSET), đường dẫn file (FILE), hoặc URL (URL).
 *
 * Dùng với [NudeNetDetector.from]. Ví dụ:
 * ```
 * ModelSource(NudeNetModel.N320, ModelSourceType.ASSET, "nudenet_320n.onnx")
 * ModelSource(NudeNetModel.M640, ModelSourceType.URL, "https://.../640m.onnx")
 * ```
 */
data class ModelSource(
    val model: NudeNetModel,
    val type: ModelSourceType,
    val path: String,
)
