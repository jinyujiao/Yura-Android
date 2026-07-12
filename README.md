# Yura Android

[English](#english) · [中文](#中文)

<a id="english"></a>

## English

**Yura** is a local-first Android reading app for EPUB and TXT books. It combines a clean Jetpack Compose interface with the [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) for EPUB rendering, reading preferences, navigation, bookmarks, and reading locations.

### Features

- **Local library** — import EPUB and TXT files, automatically convert TXT to EPUB for a consistent reading experience, keep books in app-private storage, and restore the most recent reading position.
- **Bookshelf management** — display book metadata and progress, replace a cover image, and remove a book together with its local files and bookmarks.
- **Flexible reader** — chapter navigation, table of contents, bookmarks, progress tracking, and saved reading locations.
- **Reading preferences** — light, dark, sepia, or follow-system themes; adjustable font size, line height, paragraph indentation and spacing, letter spacing, scrolling/paginated reading, publisher styles, and one/two-column layouts.
- **Read aloud (TTS)** — Android system TTS plus optional Xiaomi MiMo and Microsoft Azure Speech voices. Playback supports pause/resume, speed selection, background media controls, automatic next-chapter reading, and paragraph highlighting.
- **WebDAV sync** — manually upload/download library files, cover images, and reading progress. Sync merges progress and does not delete local data.
- **Adaptive orientation** — phones use portrait orientation; tablets (minimum width `600dp`) may rotate freely.

### Getting started

1. Install and open Yura on an Android device.
2. Tap **+** on the Library screen and select an EPUB or TXT file.
3. Select a book to read. Tap the reading area to reveal reader controls.
4. Open **Settings** to configure reading, TTS, or WebDAV sync.

### TTS and WebDAV notes

- Android system TTS works with voice engines installed on the device.
- MiMo and Microsoft Azure TTS require credentials entered in **TTS Settings**. These services use the network and may incur provider charges.
- WebDAV requires a server URL, username, password, and remote path. Sync is manual and keeps local books intact.
- Credentials are encrypted on-device with Android Keystore. Do not share screenshots or backups containing your access keys or passwords.

### Build from source

#### Requirements

- Android Studio with its bundled JDK 21
- Android SDK Platform 36
- Android device or emulator running Android 6.0 (API 23) or later

#### Build

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`.


### Testing, CI, and releases

- Run app unit tests with `./gradlew :app:testDebugUnitTest` (PowerShell: `./gradlew.bat :app:testDebugUnitTest`).
- The repository includes an Android instrumentation test for the Room 3→4 migration; run it from a connected device or emulator with `./gradlew :app:connectedDebugAndroidTest`.
- GitHub Actions runs app unit tests and `:app:assembleDebug` for pushes and pull requests targeting `main`.
- Release builds enable R8 and resource shrinking. Supply signing values outside the repository through Gradle properties: `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.
- Use `APP_VERSION_NAME` and `APP_VERSION_CODE` to override release version metadata; CI also uses `GITHUB_RUN_NUMBER` as a fallback build number.

### Project structure

```text
app/        Yura application: Compose UI, library, reader, TTS, and WebDAV sync
readium/    Readium Kotlin Toolkit sources, maintained as a Git subtree
buildSrc/   Shared Gradle build logic from the toolkit
scripts/    Maintenance scripts, including the Readium subtree updater
docs/       Project maintenance documentation
```

### Updating Readium

`readium/` is managed as a Git subtree from the [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit). Start with a clean working tree, then run:

```powershell
.\scripts\update-readium-subtree.ps1
.\gradlew.bat :app:assembleDebug
```

The script uses Readium's `develop` branch by default. See [docs/readium-subtree.md](docs/readium-subtree.md) for details and for selecting another branch or tag.

### Technology

- Kotlin and Jetpack Compose / Material 3
- Readium Kotlin Toolkit
- Room database
- Media3 media session and Android TTS
- OkHttp-based WebDAV client

---

<a id="中文"></a>

## 中文

**Yura** 是一款以本地阅读为核心的 Android 电子书应用，支持 EPUB 与 TXT。项目使用 Jetpack Compose 构建界面，并基于 [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) 实现 EPUB 渲染、目录导航、阅读位置、书签和阅读偏好设置。

### 已实现功能

- **本地书架**：导入 EPUB、TXT 文件；TXT 会自动转换为 EPUB，以获得统一的阅读体验；书籍保存在应用私有目录，并自动恢复最近阅读位置。
- **书架管理**：显示书籍信息和阅读进度；可更换封面；删除书籍时同时清理对应本地文件与书签。
- **阅读器**：支持目录、章节导航、书签、阅读进度记录和阅读位置恢复。
- **阅读设置**：支持浅色、深色、护眼色和跟随系统主题；可调整字号、行高、段首缩进、段间距、字间距；支持滚动/翻页、书籍原始样式和单栏/双栏排版。
- **朗读（TTS）**：支持 Android 系统语音，以及可选的小米 MiMo 和微软 Azure Speech 语音服务；支持暂停/继续、倍速、后台媒体控制、自动朗读下一章和段落高亮。
- **WebDAV 同步**：可手动上传或下载书籍文件、封面和阅读进度；同步时会合并进度，不会删除本地数据。
- **设备适配**：手机固定竖屏；最小宽度达到 `600dp` 的平板可自由旋转。

### 使用方法

1. 在 Android 设备上安装并打开 Yura。
2. 在书架页点击右上角 **+**，选择 EPUB 或 TXT 文件导入。
3. 点击书籍开始阅读；点击阅读区域可显示阅读器控制栏。
4. 在 **设置** 中配置阅读样式、朗读服务或 WebDAV 同步。

### TTS 与 WebDAV 说明

- 系统朗读使用设备已经安装的 Android TTS 语音引擎。
- 小米 MiMo 和微软 Azure Speech 需要在 **朗读设置** 中填写服务凭证；它们会使用网络，并可能产生服务商费用。
- WebDAV 需要配置服务器地址、用户名、密码和远程路径；同步为手动触发，且不会删除本地书籍。
- 凭证使用 Android Keystore 在设备上加密保存。请不要分享包含 API Key、密码或同步配置的截图和备份。

### 从源码构建

#### 环境要求

- 已安装 Android Studio（使用其自带的 JDK 21）
- Android SDK Platform 36
- Android 6.0（API 23）及以上的真机或模拟器

#### 构建命令

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

生成的调试 APK 位于 `app/build/outputs/apk/debug/`。


### 测试、CI 与发布

- App 单元测试：`./gradlew :app:testDebugUnitTest`（PowerShell 使用 `./gradlew.bat :app:testDebugUnitTest`）。
- 仓库包含 Room 3→4 数据库迁移的 Android 仪器测试；连接真机或模拟器后运行 `./gradlew :app:connectedDebugAndroidTest`。
- GitHub Actions 会在推送或向 `main` 发起 Pull Request 时运行 App 单元测试和 `:app:assembleDebug`。
- Release 构建已启用 R8 与资源压缩。签名信息必须通过仓库外的 Gradle 属性提供：`RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。
- 可用 `APP_VERSION_NAME`、`APP_VERSION_CODE` 覆盖版本号；CI 会以 `GITHUB_RUN_NUMBER` 作为构建号后备值。

### 目录结构

```text
app/        Yura 应用代码：Compose 界面、书架、阅读器、朗读和 WebDAV 同步
readium/    Readium Kotlin Toolkit 源码，以 Git subtree 方式维护
buildSrc/   来自工具包的共享 Gradle 构建逻辑
scripts/    维护脚本，包括 Readium subtree 更新脚本
docs/       项目维护文档
```

### 更新 Readium

`readium/` 通过 Git subtree 管理，来源为 [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit)。请先确保工作区没有未提交改动，再执行：

```powershell
.\scripts\update-readium-subtree.ps1
.\gradlew.bat :app:assembleDebug
```

该脚本默认同步 Readium 的 `develop` 分支。切换到其他分支或标签、以及更多维护说明，请查看 [docs/readium-subtree.md](docs/readium-subtree.md)。
