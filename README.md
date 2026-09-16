# NudeNet Android

Phát hiện NSFW on-device (offline) cho Android bằng model [NudeNet](https://github.com/notAI-tech/NudeNet) (YOLOv8) chạy trên ONNX Runtime.

[![](https://jitpack.io/v/haidn1609/NudeNetDemo.svg)](https://jitpack.io/#haidn1609/NudeNetDemo)

Repo gồm 2 module:
- **`nudenet`** — thư viện (publish lên JitPack). Không kèm model.
- **`app`** — demo minh họa cách dùng.

## Cài đặt (JitPack)

**1.** Thêm repo JitPack vào `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**2.** Thêm dependency (đổi `<tag>` thành version release, vd `1.0`):

```kotlin
implementation("com.github.haidn1609:NudeNetDemo:Tag")
```

> Module `nudenet` đã `api(onnxruntime-android)` nên onnxruntime được kéo tự động — không cần khai thêm.

## Sử dụng

Model **không** kèm trong thư viện — app tự cung cấp qua `ModelSource`:

```kotlin
// 320n nhúng trong assets của app:
val source = ModelSource(NudeNetModel.N320, ModelSourceType.ASSET, "nudenet_320n.onnx")
val detector = NudeNetDetector.from(context, source)

// hoặc từ file / URL:
// ModelSource(NudeNetModel.M640, ModelSourceType.FILE, "/path/640m.onnx")
// ModelSource(NudeNetModel.M640, ModelSourceType.URL,  "https://.../640m.onnx")

// detect (blocking — gọi ở thread nền):
val boxes = detector.detect(bitmap)          // List<Detection>: label + score + box
val nsfw  = detector.isNsfw(bitmap)          // Boolean
detector.close()
```

Tải model `.onnx` từ [NudeNet releases](https://github.com/notAI-tech/NudeNet/releases) (đổi tên thành `nudenet_320n.onnx` / `nudenet_640m.onnx`).

## Phát hành version mới lên JitPack

1. Push code lên GitHub.
2. Tạo **Release / tag** trên GitHub (vd `1.0`).
3. JitPack tự build tag đó — xem log tại `https://jitpack.io/#haidn1609/NudeNetDemo`.
