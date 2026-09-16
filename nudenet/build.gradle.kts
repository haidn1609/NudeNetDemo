plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "eztech.mobile.aigirlfriend.nudenet"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    // Biến thể 'release' để maven-publish/JitPack đóng gói AAR (kèm sources jar).
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // api (không phải implementation): NudeNetDetector lộ OrtSession/OnnxTensor ra API public,
    // nên consumer cần thấy onnxruntime lúc compile.
    api(libs.onnxruntime.android)
}

// JitPack build từ git tag rồi phục vụ artifact. Consumer khai báo:
//   implementation("com.github.haidn1609.NudeNetDemo:nudenet:<tag>")
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.haidn1609"
            artifactId = "nudenet"
            version = "1.0"
            afterEvaluate { from(components["release"]) }
        }
    }
}
