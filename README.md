# DysyncApp — 抖小云 Android 客户端

一个基于 WebView 的 Android 应用，用于在手机上访问局域网内的抖小云（NAS）服务。支持代理穿透，可在外部网络环境下使用。

## 功能特性

- **WebView 内嵌浏览** — 在应用内直接加载抖小云 Web 界面
- **代理穿透** — 支持通过 HTTP 代理访问局域网内不可公网暴露的目标服务
- **下拉刷新** — 内置 SwipeRefresh 下拉刷新
- **长按复制链接** — 点击网页中的链接可复制 URL 到剪贴板
- **下载管理** — 网页中的下载请求自动调用系统 DownloadManager
- **Scheme 唤起** — 支持 `dysync://` Scheme 从其他应用唤起
- **设置页** — 可配置代理开关、代理地址、目标 URL

## 项目结构

```
dysync-android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/dysync/
│   │   │   ├── MainActivity.kt          # 主界面、WebView 配置、下载处理
│   │   │   ├── ProxyUtils.kt           # 代理配置、SSL 信任、Cookie 同步
│   │   │   ├── SettingsActivity.kt     # 设置页（代理配置）
│   │   │   └── DysyncApplication.kt   # Application 类
│   │   ├── res/
│   │   │   ├── mipmap-*/              # 应用图标（各分辨率）
│   │   │   ├── xml/
│   │   │   │   ├── backup_rules.xml    # 备份规则
│   │   │   │   ├── data_extraction_rules.xml  # 数据提取规则
│   │   │   │   └── network_security_config.xml
│   │   │   └── menu/main_menu.xml      # 顶部菜单（刷新/主页/浏览器/设置）
│   │   └── build.gradle.kts
├── docs/BUILD.md                       # 构建指南与已知问题
├── .github/workflows/build-apk.yml     # GitHub Actions 自动构建
├── build.gradle.kts
└── settings.gradle.kts
```

## 环境要求

- **JDK 21**
- **Android SDK**（API 级别 34）
- **Gradle 8.7**

## 本地构建

```bash
# 安装依赖
./gradlew clean

# 构建 Release APK
./gradlew assembleRelease

# 输出路径
app/build/outputs/apk/release/app-release.apk
```

## CI/CD

项目已配置 GitHub Actions 工作流（`.github/workflows/build-apk.yml`），每次推送到 `main` 分支或手动触发 `workflow_dispatch` 时自动：

1. 签出代码
2. 安装 JDK 21
3. 接受 Android SDK 许可证
4. 安装 SDK 平台和构建工具
5. 安装 Gradle 8.7
6. 生成有效的 PNG 图标（ImageMagick）
7. 执行 `gradle assembleRelease`
8. 上传 APK 为 GitHub Actions Artifact

构建成功后，在 Actions 页面的 Artifacts 区域下载 `DysyncApp-APK`。

## 已知问题与修复记录

### 1. PNG 图标文件损坏
**现象**：AAPT2 编译 `mipmap-*/ic_launcher.png` 时崩溃。
**原因**：仓库中已有的 PNG 文件损坏或格式不兼容。
**修复**：在 CI 工作流中加入 ImageMagick 步骤，强制删除旧 PNG 后重新生成。

### 2. Kotlin 编译错误（API 34 兼容性）
以下 API 在 Android 14（API 34）中被移除或行为变更：

| 问题 | 修复 |
|------|------|
| `supportZoom = true` | → `setSupportZoom(true)` |
| `setAppCacheEnabled()` / `setAppCachePath()` | 删除（API 34 已移除） |
| `downloadListener` 属性 | → `setDownloadListener { }` |
| `requestProperty()` | → `setRequestProperty()` |
| `ClipboardManager.from()` | → `getsystemService(Context.CLIPBOARD_SERVICE)` |
| `onReceivedSslError()` / `SslError` | 删除（类在 API 34 中不可用） |
| `domain="files"` | → `domain="file"`（backup_rules / data_extraction_rules） |

### 3. GitHub Release 创建失败
**现象**：`actions/create-release@v1` 和 `actions/upload-release-asset@v1` 已废弃，导致 tag 冲突和 multipart 错误。
**修复**：改用 `actions/upload-artifact@v4` 直接上传 APK 为 Artifact。

## 配置说明

### 代理设置
在设置页中可配置：
- **代理开关** — 是否启用 HTTP 代理
- **代理地址** — 代理服务器 IP/域名
- **代理端口** — 代理服务器端口
- **目标 URL** — 抖小云 NAS 的地址（默认 `http://192.168.5.9:10101`）

### Scheme 唤起
应用注册了 `dysync://` Scheme，可通过以下方式唤起：
```bash
adb shell am start -W -a android.intent.action.VIEW -d "dysync://example.com" com.example.dysync
```

## 许可证

MIT License