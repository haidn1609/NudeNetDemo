package eztech.mobile.aigirlfriend.nudenet

/**
 * Cấu hình model NudeNet. Model KHÔNG còn nhúng trong assets — nạp từ file ngoài
 * hoặc URL (xem [NudeNetDetector.fromFile] / [NudeNetDetector.fromUrl]).
 *
 * [inputSize] là cạnh input vuông (phải khớp model), [fileName] là tên file gợi ý
 * dùng khi cache/tải về.
 */
enum class NudeNetModel(val inputSize: Int, val fileName: String) {
    /** 320x320, dựa YOLOv8n — nhẹ & nhanh (mặc định). */
    N320(320, "nudenet_320n.onnx"),

    /** 640x640, dựa YOLOv8m — chính xác hơn nhưng nặng/chậm hơn. */
    M640(640, "nudenet_640m.onnx"),
}
