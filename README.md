# Yura

<p align="center">
  <strong>一款专注于长文本阅读体验的本地优先 Android 阅读器</strong>
</p>

<p align="center">
  <a href="https://github.com/jinyujiao/Yura-Android/actions/workflows/android-ci.yml"><img src="https://github.com/jinyujiao/Yura-Android/actions/workflows/android-ci.yml/badge.svg" alt="Android CI"></a>
  <a href="https://github.com/jinyujiao/Yura-Android/releases/latest"><img src="https://img.shields.io/github/v/release/jinyujiao/Yura-Android?display_name=tag&sort=semver" alt="Latest release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-4c8bf5" alt="Apache License 2.0"></a>
</p>

Yura 是一款本地优先的 EPUB / TXT 阅读器。它使用 Jetpack Compose 构建应用界面，使用 Readium Kotlin Toolkit 负责 EPUB 的排版、导航和阅读定位。

Yura 不追求堆叠功能，而是专注于几件事：**稳定阅读、舒服排版、可靠朗读，以及让书籍和阅读记录真正属于用户自己。**

## 当前版本

- 最新版本：`1.3.2`
- 最低支持：Android 11（API 30）
- 发布渠道：[GitHub Releases](https://github.com/jinyujiao/Yura-Android/releases)

## 功能

### 阅读

- 导入 EPUB 和 TXT，支持本地文件夹多选批量导入。
- TXT 自动转换为 EPUB，统一阅读体验。
- 支持从系统文件管理器或其他应用直接使用 Yura 打开 EPUB / TXT。
- 分页和滚动阅读模式。
- 手机、平板自适应布局；平板横屏支持双栏阅读。
- 目录导航、书签、阅读进度恢复。
- 自动、浅色、深色和护眼主题。
- 可调节字号、字体、行高、段首缩进、段间距和字间距。
- 可选择是否使用 EPUB 原始版式。

### 标注与修订

- 长按文字后可复制、添加笔记、高亮或创建修订。
- 笔记、高亮和修订按图书及章节归类。
- 支持从标注跳转回原文，且不会改变当前阅读进度。
- 支持删除标注。
- 可将修订应用到原始 EPUB，导出一份新的修订版 EPUB，不修改原书。

### TTS 朗读

- 支持 Android 系统 TTS。
- 支持可选的小米 MiMo 和 Microsoft Azure Speech 云端音色。
- 支持音色选择、语速调节和睡眠定时器。
- 段落高亮同步。
- 支持后台朗读、媒体通知和锁屏控制。
- 支持自动续读下一章节。
- 朗读过程中保存阅读进度，应用异常退出后也能恢复到较新的位置。

### 阅读统计与同步

- 统计阅读时长、阅读天数和阅读趋势。
- 支持 7 天、30 天趋势查看。
- WebDAV 可同步书籍、封面、阅读进度、书签、笔记、高亮、修订和阅读统计。
- 同步采用合并策略，尽量避免因为一台设备的本地状态而误删另一台设备的数据。

## 截图

项目目前暂未固定截图展示。后续会补充手机竖屏、平板横屏和深色模式下的实际界面截图。

## 构建

### 环境要求

- Android Studio
- Android Studio 自带的 JDK 21
- Android SDK Platform 36
- Windows PowerShell（仓库脚本以 PowerShell 为主）

### Debug 构建

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

Debug APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 本地 Release 构建

使用本机调试证书、仅用于设备测试的 Release：

```powershell
.\gradlew.bat :app:assembleLocalRelease
```

正式发布包需要使用独立签名证书。签名配置、版本递增、GitHub Actions Secrets 和 Release 发布流程，请查看 [RELEASING.md](RELEASING.md)。

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

推送到 `main` 或向 `main` 提交 Pull Request 时，GitHub Actions 会自动执行单元测试、Lint 和 Debug 构建。

## 项目结构

```text
app/        Yura 应用：书架、阅读器、标注、统计和 WebDAV
tts-core/   平台无关的 TTS 文本清洗、分句和播放策略
tts/        Android TTS、云端 TTS、Media3 播放和后台媒体服务
readium/    以 Git subtree 维护的 Readium Kotlin Toolkit 源码
buildSrc/   Gradle 构建逻辑
scripts/    项目维护脚本，包括 Readium subtree 更新脚本
docs/       项目维护文档
```

## 更新 Readium

`readium/` 通过 Git subtree 跟踪 Readium Kotlin Toolkit。请先确保工作区干净，再运行：

```powershell
.\scripts\update-readium-subtree.ps1
```

更新后建议重点验证分页、文字选择、目录跳转、阅读进度、TTS 和横竖屏布局。

## 数据与隐私

- 书籍、阅读进度和标注默认保存在应用本地私有目录。
- WebDAV、MiMo 和 Microsoft Azure Speech 都是可选功能，只有配置并主动使用后才会连接网络。
- API 凭据通过 Android Keystore 支持的加密存储保存在设备上。
- Yura 不会因为关闭网络服务而影响本地阅读。
- 请勿将签名证书、密码、API Key、`local.properties` 或正式构建产物提交到仓库。

## 许可证

- Yura 原创代码采用 [Apache License 2.0](LICENSE)。
- `readium/` 中的 Readium Kotlin Toolkit 修改及 Readium CSS 按其原有 [BSD 3-Clause License](readium/LICENSE) 分发。
- 内置字体和其他第三方依赖按各自许可证分发，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
- 应用内也可以通过“设置 → 关于 → 开源许可”查看相关声明。

## 贡献

欢迎提交 Issue、改进建议和 Pull Request。

涉及阅读器核心、Readium、TTS、同步或数据库的改动，建议在提交前至少完成对应模块测试，并在真实手机和平板上验证关键场景。

---

## English

Yura is a local-first EPUB / TXT reader for Android, focused on comfortable long-form reading, reliable text-to-speech, and user-owned local data.

### Highlights

- Import EPUB and TXT files, including multi-select batch import.
- Open supported books directly from Android file managers and other apps.
- Paginated or scrolling reading with table of contents, bookmarks, and progress restoration.
- Adaptive phone and tablet layouts, including two-column reading in tablet landscape mode.
- Customizable theme, font, font size, line height, indentation, spacing, and publisher styles.
- Copy, notes, highlights, and text corrections with chapter-aware navigation.
- Export a corrected copy of an EPUB without modifying the original book.
- Android system TTS, Xiaomi MiMo, and Microsoft Azure Speech voices.
- Paragraph highlighting, playback speed, sleep timer, background playback, and automatic chapter continuation.
- Reading statistics and optional WebDAV synchronization for books and reading data.

### Build

Requirements:

- Android Studio with JDK 21
- Android SDK Platform 36
- Android 11 / API 30 or newer for runtime support

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

For production signing, version management, local release verification, and GitHub Release automation, see [RELEASING.md](RELEASING.md).

### License

Original Yura source code is licensed under the [Apache License 2.0](LICENSE). Readium sources and Readium CSS retain their [BSD 3-Clause License](readium/LICENSE). Other dependencies and bundled fonts retain their respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
