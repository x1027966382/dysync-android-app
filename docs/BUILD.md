# DysyncApp 构建指南

## 环境要求

- JDK 21
- Gradle 8.7
- Android SDK API 34
- Android Build Tools 34.0.0

## 本地构建

```bash
# 安装依赖
./gradlew clean

# 构建 Release APK
./gradlew assembleRelease

# 输出路径
# app/build/outputs/apk/release/app-release.apk
```

## GitHub Actions 自动构建

工作流文件：`.github/workflows/build-apk.yml`

### 触发方式

- 推送到 `main` 分支自动触发
- 手动触发（Actions → workflow_dispatch）

### 关键步骤

1. **Checkout** — 拉取代码
2. **Set up JDK 21** — 安装 Temurin JDK
3. **Accept Android SDK Licenses** — 接受 SDK 许可证
4. **Install Android SDK Platform & Build Tools** — 安装 API 34 和构建工具
5. **Install Gradle** — 下载 Gradle 8.7
6. **Generate Valid Icons** — 用 ImageMagick 生成有效 PNG 图标
7. **Build Release APK** — 执行 `gradle assembleRelease`
8. **Create GitHub Release** — 创建 Release
9. **Upload APK to Release** — 上传 APK

## 已知问题与修复

### 1. PNG 图标文件损坏

**症状**：`AAPT2 aapt2-8.5.0-11315950-linux Daemon: Unexpected error during compile 'ic_launcher.png'`

**原因**：项目中现有的 `mipmap-*` 目录下的 PNG 图标文件损坏或格式不兼容。

**修复**：在 CI 构建前，用 ImageMagick 重新生成所有密度的图标：

```bash
sudo apt-get update -y && sudo apt-get install -y imagemagick
for dpi in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  case $dpi in
    mdpi) size=48 ;;
    hdpi) size=72 ;;
    xhdpi) size=96 ;;
    xxhdpi) size=144 ;;
    xxxhdpi) size=192 ;;
  esac
  convert -size ${size}x${size} xc:"#FE2C55" -fill white -gravity center -pointsize $((size/2)) -annotate +0+0 "抖" app/src/main/res/mipmap-$dpi/ic_launcher.png
  cp app/src/main/res/mipmap-$dpi/ic_launcher.png app/src/main/res/mipmap-$dpi/ic_launcher_round.png
done
```

### 2. Kotlin 编译错误（API 34 兼容性）

**症状**：`compileReleaseKotlin` 任务失败，报错包括：
- `Function invocation 'supportZoom()' expected`
- `Unresolved reference 'setAppCacheEnabled'`
- `Unresolved reference 'SslError'`
- `Unresolved reference 'downloadListener'`
- `Unresolved reference 'DownloadManager'`
- `Unresolved reference 'requestProperty'`

**原因**：代码使用了已废弃或不存在的 API。

**修复**：

| 问题 | 旧代码 | 新代码 |
|------|--------|--------|
| supportZoom | `supportZoom = true` | `setSupportZoom(true)` |
| AppCache | `setAppCacheEnabled(true)` / `setAppCachePath(...)` | 删除（API 34 已移除） |
| SSL 错误 | 缺少 import | `import android.webkit.SslError` / `import android.webkit.SslErrorHandler` |
| 下载监听 | `WebView.downloadListener = ...` | `WebView.setDownloadListener(...)` |
| 下载管理器 | 缺少 import | `import android.os.Environment` / `import android.download.DownloadManager` |
| 请求属性 | `requestProperty(...)` | `setRequestProperty(...)` |
| 主机名校验 | `conn.hostnameVerifier = { _, _ -> true }` | `conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }` |

### 3. AndroidManifest.xml 命名空间警告

**症状**：`package="com.example.dysync" found in source AndroidManifest.xml... is no longer supported`

**说明**：这是 Gradle AGP 8.x 的警告，不影响构建。如需修复，在 `AndroidManifest.xml` 中移除 `package` 属性。

## 依赖

### build.gradle.kts (app 模块)

```kotlin
plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.example.dysync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.dysync"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
```

## 调试技巧

### 查看完整日志

```bash
# 在项目根目录执行
./gradlew assembleRelease --info 2>&1 | tee build.log
```

### 清理并重新构建

```bash
./gradlew clean
./gradlew assembleRelease
```

### 检查 APK 内容

```bash
# 解压 APK
unzip app-release.apk -d apk_contents

# 查看图标
ls -la apk_contents/res/mipmap-*/
```