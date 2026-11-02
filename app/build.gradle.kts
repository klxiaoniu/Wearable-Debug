import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// 执行系统命令获取输出
fun getCommandOutput(vararg command: String, default: String): String {
    return try {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        process.waitFor()
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        default
    }
}

// 获取Git提交次数和简短Hash
val gitCommitCount = getCommandOutput("git", "rev-list", "--count", "HEAD", default = "1").toIntOrNull() ?: 1
val gitCommitHash = getCommandOutput("git", "rev-parse", "--short", "HEAD", default = "unknown")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "test.hook.debug"
    compileSdk = 36

    defaultConfig {
        applicationId = "test.hook.debug"
        minSdk = 26
        targetSdk = 36
        versionCode = gitCommitCount
        versionName = gitCommitHash
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
        named("debug") {
            versionNameSuffix = "-debug-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    implementation("com.github.kyuubiran:EzXHelper:2.2.1")
    implementation("org.luckypray:dexkit:2.0.7")
}