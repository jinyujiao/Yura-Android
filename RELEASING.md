# Yura 正式发布流程

项目使用根目录的 `version.properties` 作为唯一版本源：

```properties
VERSION_CODE=1
VERSION_NAME=1.0.0
```

- `VERSION_CODE`：Android 内部升级编号，每次正式发布必须增加。
- `VERSION_NAME`：用户可见的语义化版本号，格式为 `主版本.次版本.修订版本`。
- 普通 Gradle 构建不会修改版本号；只有 `release.ps1` 会按要求递增。

## 发布前开源许可检查

- 新增或升级运行时依赖后，核对其许可证并更新 `THIRD_PARTY_NOTICES.md`。
- 保持 `readium/LICENSE` 与当前 Readium 上游 BSD 3-Clause 文本一致。
- 同步更新 `app/src/main/assets/licenses/open_source_licenses.txt`，确保正式 APK/AAB 内可以查看完整声明。
- 在应用“设置 → 关于 → 开源许可”中确认许可内容可以正常打开和滚动。
## 本机签名配置

在 `~/.gradle/gradle.properties` 中配置，禁止提交到仓库：

```properties
RELEASE_STORE_FILE=C:/absolute/path/yura-release.jks
RELEASE_STORE_PASSWORD=your-store-password
RELEASE_KEY_ALIAS=your-key-alias
RELEASE_KEY_PASSWORD=your-key-password
```

正式的 `assembleRelease` 和 `bundleRelease` 会校验这四项配置及证书文件；缺失时构建直接失败。`assembleLocalRelease` 仍然是调试证书包，不可用于正式发布。

## 本地构建

生成当前版本，不递增：

```powershell
.\release.ps1 -Bump none
```

生成下一个修订版本，例如 `1.0.0 -> 1.0.1`，并让 `VERSION_CODE + 1`：

```powershell
.\release.ps1
```

生成下一个次版本或主版本：

```powershell
.\release.ps1 -Bump minor
.\release.ps1 -Bump major
```

脚本自动执行：

1. 更新 `version.properties`。
2. 运行单元测试、Android Test Kotlin 编译和 Lint。
3. 构建正式签名 APK 与 AAB。
4. 使用 `apksigner` 验证 APK 签名。
5. 输出重命名后的文件和 SHA-256 到 `dist/`。
6. 构建或验证失败时恢复原版本文件。

输出示例：

```text
dist/Yura-1.0.1.apk
dist/Yura-1.0.1.aab
dist/SHA256SUMS.txt
```

## 发布到 GitHub

先提交所有应用代码，确保工作区干净，然后运行：

```powershell
.\release.ps1 -Publish
```

该命令会：

1. 递增修订版本和 `VERSION_CODE`。
2. 完成全部本地验证和正式包构建。
3. 提交 `version.properties`，提交信息为 `Release vX.Y.Z`。
4. 创建带注释 Git Tag `vX.Y.Z`。
5. 推送当前分支和 Tag。
6. 由 GitHub Actions 构建并创建 GitHub Release。

如果需要发布当前版本且不递增：

```powershell
.\release.ps1 -Bump none -Publish
```

## GitHub Release Environment

正式发布 Workflow 使用名为 `release` 的 GitHub Environment，不读取普通 Repository Secrets。

在仓库中完成以下设置：

1. 打开 `Settings -> Environments`。
2. 创建 Environment：`release`。
3. 在 `Deployment branches and tags` 中限制为受保护 Tag，例如 `v*`。
4. 建议添加 Required reviewers，使正式发布必须人工批准。
5. 在该 Environment 的 `Environment secrets` 中添加：

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Workflow 只在解码证书的步骤读取 `RELEASE_KEYSTORE_BASE64`，另外三个密码只在 Gradle 正式构建步骤中可见，不会作为整个 Job 的全局环境变量传给其他 Action。

在 Windows PowerShell 中生成证书 Base64 文本：

```powershell
[Convert]::ToBase64String(
    [IO.File]::ReadAllBytes("C:\absolute\path\yura-release.jks")
) | Set-Clipboard
```

将剪贴板内容保存为 Environment Secret `RELEASE_KEYSTORE_BASE64`。Base64 不是加密，不要把 Base64、证书或密码写入仓库、Issue、聊天或构建日志。

## GitHub 自动发布

推送格式为 `vX.Y.Z` 的 Tag 后，`.github/workflows/release.yml` 会：

1. 校验 Tag 与 `VERSION_NAME` 完全一致。
2. 从 GitHub Secrets 恢复临时签名证书。
3. 运行测试、Lint、Release APK 和 AAB 构建。
4. 验证 APK 签名并生成 SHA-256。
5. 上传 Actions Artifact。
6. 自动创建带生成式更新日志的 GitHub Release。

Google Play 应上传 `.aab`；直接分发或侧载可使用 `.apk`。后续版本必须继续使用同一份签名证书，并保证 `VERSION_CODE` 单调增加。