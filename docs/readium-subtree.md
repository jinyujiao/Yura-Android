# Readium subtree

The `readium/` directory is managed as a Git subtree from:

```text
https://github.com/readium/kotlin-toolkit.git
```

The local baseline branch for the current vendored copy is:

```text
readium-baseline
```

To update Readium later, first make sure the working tree is clean, then run:

```powershell
.\scripts\update-readium-subtree.ps1
```

By default the script pulls from the upstream `develop` branch. To use a different branch or tag:

```powershell
.\scripts\update-readium-subtree.ps1 -Branch <branch-or-tag>
```

After updating, verify the app:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

If Readium changes APIs, the app integration points most likely to need edits are:

```text
app/src/main/java/com/yura/app/library/ReadiumServices.kt
app/src/main/java/com/yura/app/reader/ReaderActivity.kt
settings.gradle.kts
app/build.gradle.kts
```
