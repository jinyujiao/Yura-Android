# Yura Android

[English](#english)

Yura 是一款本地优先的 Android EPUB / TXT 阅读器，使用 Jetpack Compose 构建界面，并基于 Readium Kotlin Toolkit 提供 EPUB 排版、导航与阅读定位能力。

项目当前聚焦于稳定、舒适的长文本阅读体验：本地书架、细致排版、文字标注、TTS 朗读，以及可选的 WebDAV 多设备同步。

## 主要功能

- **本地书架**：导入 EPUB 与 TXT，保存阅读进度、封面和书籍信息；TXT 会转换为 EPUB，以统一阅读体验。
- **系统文件打开**：可从 Android 文件管理器或其他应用直接选择 Yura 打开或分享 EPUB、TXT 文件。
- **Readium 阅读器**：支持目录导航、书签、阅读进度、滚动与分页模式，以及单栏 / 双栏排版。
- **阅读排版**：自动、浅色、深色和护眼主题；可调整字号、行高、段首缩进、段间距、字间距，并可关闭书籍自带版式。
- **稳定文字选择**：针对分页模式中跨页长段落的选择闪烁进行了修复，选区期间不会误触发翻页。
- **高亮与笔记**：选中文字后可复制、添加高亮或下划线笔记；笔记页按图书归类展示，并支持删除。
- **TTS 朗读**：支持 Android 系统 TTS，以及可选的小米 MiMo、Microsoft Azure Speech；提供语速调节、段落高亮、后台媒体控制和自动续读下一章。
- **WebDAV 同步**：手动同步书籍、封面、阅读进度、书签、高亮和笔记；同步过程以合并为主，不会因为远端缺失而直接删除本地书籍。
- **自适应方向**：手机默认竖屏，最小宽度达到 `600dp` 的平板设备可自由旋转。

## 环境要求

- Android Studio 与其自带的 JDK 21
- Android SDK Platform 36
- Android 6.0（API 23）或更高版本的设备或模拟器

## 构建

在 Windows PowerShell 中执行：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

调试 APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

如需生成使用本机调试证书签名、仅用于设备测试的优化包：

```powershell
.\gradlew.bat :app:assembleLocalRelease
```

正式发布包必须配置独立签名证书。完整的本地签名、版本递增、GitHub Actions Secrets 和 Release 发布流程见 [RELEASING.md](RELEASING.md)。

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

仓库中的 GitHub Actions 会在推送到 `main` 或向 `main` 提交 Pull Request 时运行单元测试、Lint 和 Debug 构建。

## 项目结构

```text
app/        Yura 应用：Compose UI、书架、阅读器、TTS 与 WebDAV 同步
readium/    以 Git subtree 维护的 Readium Kotlin Toolkit 源码
buildSrc/   Readium 与项目共用的 Gradle 构建逻辑
scripts/    项目维护脚本，包括 Readium subtree 更新脚本
docs/       项目维护文档
```

## 更新 Readium

`readium/` 通过 Git subtree 跟踪 Readium Kotlin Toolkit。请先确保工作区干净，再运行：

```powershell
.\scripts\update-readium-subtree.ps1
```

脚本默认从 `readium` remote 的 `develop` 分支获取更新。更新完成后应重新执行测试、Lint 和完整构建，并重点验证分页、选区、目录跳转、阅读进度与 TTS。

## 数据与隐私

- 书籍与阅读数据默认保存在应用本地私有目录。
- WebDAV、MiMo 和 Azure 均为可选网络功能，只有在用户配置并主动使用时才会连接相应服务。
- 凭据通过 Android Keystore 支持的加密存储保存在设备上。
- 请勿把签名证书、密码、API Key、`local.properties` 或生成的正式安装包提交到仓库。

## 许可证

- Yura 原创代码采用 [Apache License 2.0](LICENSE)。
- `readium/` 中的 Readium Kotlin Toolkit 修改及 Readium CSS 仍采用 [BSD 3-Clause](readium/LICENSE)。
- 其他依赖和内置字体按各自许可证分发，完整声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
- 正式 APK/AAB 内同样包含上述开源许可，可在“设置 → 关于 → 开源许可”中查看。

Yura 正在持续开发中。首次正式发布版本为 `1.0.0`。

---

<a id="english"></a>

## English

Yura is a local-first EPUB and TXT reader for Android. It uses Jetpack Compose for the application interface and the Readium Kotlin Toolkit for EPUB rendering, navigation, and reading locations.

### Highlights

- Import EPUB and TXT books into a private local library.
- Open or share supported book files directly from Android file managers and other apps.
- Read with paginated or scrolling layouts, table of contents, bookmarks, progress restoration, and configurable typography.
- Use stable text selection in paginated content, with copy, highlight, and underlined note actions.
- Browse annotations grouped by book and delete individual highlights or notes.
- Read aloud with Android system TTS or optional Xiaomi MiMo and Microsoft Azure Speech voices.
- Synchronize books, covers, reading progress, bookmarks, highlights, and notes through optional WebDAV.
- Build for Android 6.0 / API 23 and later with JDK 21 and Android SDK 36.

### Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

For production signing, version management, local release verification, and GitHub Release automation, see [RELEASING.md](RELEASING.md).

### Security

Do not commit release keystores, passwords, API credentials, `local.properties`, APK/AAB artifacts, or generated files under `dist/`. Network-backed TTS and WebDAV are optional and only operate after user configuration.

### License

Original Yura source code is licensed under the [Apache License 2.0](LICENSE). Readium sources and Readium CSS retain their [BSD 3-Clause license](readium/LICENSE). Other dependencies and bundled fonts retain their respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The same notices are available inside the app under Settings → About → Open-source licenses.
