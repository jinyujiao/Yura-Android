[CmdletBinding()]
param(
    [ValidateSet("major", "minor", "patch", "none")]
    [string]$Bump = "patch",
    [switch]$Publish
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = $PSScriptRoot
$versionFile = Join-Path $repoRoot "version.properties"
$changelogFile = Join-Path $repoRoot "CHANGELOG.md"
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$distDirectory = Join-Path $repoRoot "dist"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
    }
}

function Read-AppVersion {
    $values = ConvertFrom-StringData (Get-Content -LiteralPath $versionFile -Raw)
    $versionCode = 0
    if (-not [int]::TryParse($values.VERSION_CODE, [ref]$versionCode) -or $versionCode -le 0) {
        throw "VERSION_CODE must be a positive integer."
    }
    if ($values.VERSION_NAME -notmatch '^(\d+)\.(\d+)\.(\d+)$') {
        throw "VERSION_NAME must use semantic versioning, for example 1.2.3."
    }

    [pscustomobject]@{
        Code = $versionCode
        Name = $values.VERSION_NAME
        Major = [int]$Matches[1]
        Minor = [int]$Matches[2]
        Patch = [int]$Matches[3]
    }
}

function Write-AppVersion {
    param(
        [int]$Code,
        [string]$Name
    )

    $content = "VERSION_CODE=$Code$([Environment]::NewLine)VERSION_NAME=$Name$([Environment]::NewLine)"
    [System.IO.File]::WriteAllText($versionFile, $content, $utf8NoBom)
}

function Get-LatestVersionTag {
    $tags = @(git tag --list "v[0-9]*" --sort=-version:refname)
    $gitExitCode = $LASTEXITCODE
    if ($gitExitCode -ne 0) {
        throw "Unable to inspect Git tags."
    }
    return $tags | Select-Object -First 1
}

function Get-ReleaseChanges {
    param([string]$SinceTag)

    $range = if ([string]::IsNullOrWhiteSpace($SinceTag)) { "HEAD" } else { "$SinceTag..HEAD" }
    $changes = @(git log $range --reverse --no-merges --pretty=format:%s)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to generate changelog entries from $range."
    }
    return @($changes | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_) -and
            $_ -notmatch '^Release\s+v?\d+\.\d+\.\d+$' -and
            $_ -notmatch '^\[skip changelog\]\s*'
    })
}

function Update-Changelog {
    param(
        [string]$Version,
        [string[]]$Changes
    )

    $content = if (Test-Path -LiteralPath $changelogFile -PathType Leaf) {
        Get-Content -LiteralPath $changelogFile -Raw
    } else {
        "# Changelog`n`nYura 的重要变更记录。发布条目由 ``release.ps1`` 根据上一个版本标签之后的 Git 提交自动生成。`n`n"
    }
    if ($content -match "(?m)^## \[$([regex]::Escape($Version))\](?:\s|$)") {
        throw "CHANGELOG.md already contains version $Version."
    }

    $items = if ($Changes.Count -gt 0) {
        $Changes | ForEach-Object { "- $($_.Trim())" }
    } else {
        @("- 无用户可见变更。")
    }
    $section = "## [$Version] - $(Get-Date -Format 'yyyy-MM-dd')`n`n$($items -join "`n")`n`n"
    $firstRelease = [regex]::Match($content, '(?m)^## \[')
    $updated = if ($firstRelease.Success) {
        $content.Insert($firstRelease.Index, $section)
    } else {
        $content.TrimEnd() + "`n`n" + $section
    }
    [System.IO.File]::WriteAllText($changelogFile, $updated, $utf8NoBom)
}

function Get-ReleaseNotes {
    param([string]$Version)

    if (-not (Test-Path -LiteralPath $changelogFile -PathType Leaf)) {
        throw "CHANGELOG.md is missing."
    }
    $content = Get-Content -LiteralPath $changelogFile -Raw
    $escapedVersion = [regex]::Escape($Version)
    $match = [regex]::Match($content, "(?ms)^## \[$escapedVersion\][^\r\n]*\r?\n(?<body>.*?)(?=^## \[|\z)")
    if (-not $match.Success -or [string]::IsNullOrWhiteSpace($match.Groups['body'].Value)) {
        throw "CHANGELOG.md does not contain release notes for version $Version."
    }
    return $match.Groups['body'].Value.Trim()
}

function Restore-ReleaseMetadata {
    param(
        [string]$VersionContent,
        [bool]$ChangelogExisted,
        [string]$ChangelogContent
    )

    [System.IO.File]::WriteAllText($versionFile, $VersionContent, $utf8NoBom)
    if ($ChangelogExisted) {
        [System.IO.File]::WriteAllText($changelogFile, $ChangelogContent, $utf8NoBom)
    } elseif (Test-Path -LiteralPath $changelogFile) {
        Remove-Item -LiteralPath $changelogFile -Force
    }
}

function Get-AndroidSdkDirectory {
    foreach ($candidate in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate -PathType Container)) {
            return $candidate
        }
    }

    $localProperties = Join-Path $repoRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) {
            $sdkDirectory = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\'
            if (Test-Path -LiteralPath $sdkDirectory -PathType Container) {
                return $sdkDirectory
            }
        }
    }

    throw "Android SDK was not found. Configure ANDROID_SDK_ROOT, ANDROID_HOME, or local.properties."
}

Push-Location $repoRoot
try {
    if ($Publish -or $Bump -ne "none") {
        $workingTree = git status --porcelain
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to inspect the Git working tree."
        }
        if ($workingTree) {
            throw "Version bumps and publishing require a clean Git working tree. Commit the application changes first."
        }
    }

    $originalVersionContent = Get-Content -LiteralPath $versionFile -Raw
    $changelogExisted = Test-Path -LiteralPath $changelogFile -PathType Leaf
    $originalChangelogContent = if ($changelogExisted) { Get-Content -LiteralPath $changelogFile -Raw } else { "" }
    $currentVersion = Read-AppVersion
    $nextVersionCode = $currentVersion.Code
    $nextMajor = $currentVersion.Major
    $nextMinor = $currentVersion.Minor
    $nextPatch = $currentVersion.Patch

    switch ($Bump) {
        "major" {
            $nextMajor++
            $nextMinor = 0
            $nextPatch = 0
            $nextVersionCode++
        }
        "minor" {
            $nextMinor++
            $nextPatch = 0
            $nextVersionCode++
        }
        "patch" {
            $nextPatch++
            $nextVersionCode++
        }
    }

    $nextVersionName = "$nextMajor.$nextMinor.$nextPatch"
    $tag = "v$nextVersionName"
    if ($Publish) {
        git rev-parse --quiet --verify "refs/tags/$tag" *> $null
        if ($LASTEXITCODE -eq 0) {
            throw "Tag already exists locally: $tag"
        }
    }
    try {
        if ($Bump -ne "none") {
            $releaseChanges = Get-ReleaseChanges -SinceTag (Get-LatestVersionTag)
            Update-Changelog -Version $nextVersionName -Changes $releaseChanges
            Write-AppVersion -Code $nextVersionCode -Name $nextVersionName
            Write-Host "Version: $($currentVersion.Name) ($($currentVersion.Code)) -> $nextVersionName ($nextVersionCode)"
        } else {
            Write-Host "Building current version: $nextVersionName ($nextVersionCode)"
        }

        Invoke-CheckedCommand -Command $gradleWrapper -Arguments @(
            ":app:testDebugUnitTest",
            ":app:compileDebugAndroidTestKotlin",
            ":app:lintDebug",
            ":app:assembleRelease",
            ":app:bundleRelease",
            "--no-daemon",
            "--no-configuration-cache",
            "--stacktrace"
        )

        $apkSource = Join-Path $repoRoot "app/build/outputs/apk/release/app-release.apk"
        $aabSource = Join-Path $repoRoot "app/build/outputs/bundle/release/app-release.aab"
        if (-not (Test-Path -LiteralPath $apkSource -PathType Leaf)) {
            throw "Release APK was not generated: $apkSource"
        }
        if (-not (Test-Path -LiteralPath $aabSource -PathType Leaf)) {
            throw "Release AAB was not generated: $aabSource"
        }

        $sdkDirectory = Get-AndroidSdkDirectory
        $buildToolsDirectory = Get-ChildItem -LiteralPath (Join-Path $sdkDirectory "build-tools") -Directory |
            Where-Object { $_.Name -match '^\d+(\.\d+)+$' } |
            Sort-Object { [version]$_.Name } -Descending |
            Select-Object -First 1
        if (-not $buildToolsDirectory) {
            throw "Android SDK Build Tools were not found."
        }

        $apkSigner = Join-Path $buildToolsDirectory.FullName "apksigner.bat"
        Invoke-CheckedCommand -Command $apkSigner -Arguments @("verify", "--verbose", "--print-certs", $apkSource)

        if (Test-Path -LiteralPath $distDirectory) {
            $resolvedDist = (Resolve-Path -LiteralPath $distDirectory).Path
            if (-not $resolvedDist.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing to clean an output directory outside the repository: $resolvedDist"
            }
            Remove-Item -LiteralPath $resolvedDist -Recurse -Force
        }
        New-Item -ItemType Directory -Path $distDirectory | Out-Null

        $apkOutput = Join-Path $distDirectory "Yura-$nextVersionName.apk"
        $aabOutput = Join-Path $distDirectory "Yura-$nextVersionName.aab"
        Copy-Item -LiteralPath $apkSource -Destination $apkOutput
        Copy-Item -LiteralPath $aabSource -Destination $aabOutput

        $checksumLines = @($apkOutput, $aabOutput) | ForEach-Object {
            $hash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
            "$($hash.Hash.ToLowerInvariant())  $([System.IO.Path]::GetFileName($_))"
        }
        [System.IO.File]::WriteAllLines(
            (Join-Path $distDirectory "SHA256SUMS.txt"),
            $checksumLines,
            $utf8NoBom
        )
        [System.IO.File]::WriteAllText(
            (Join-Path $distDirectory "RELEASE_NOTES.md"),
            (Get-ReleaseNotes -Version $nextVersionName) + [Environment]::NewLine,
            $utf8NoBom
        )
    } catch {
        if ($Bump -ne "none") {
            Restore-ReleaseMetadata `
                -VersionContent $originalVersionContent `
                -ChangelogExisted $changelogExisted `
                -ChangelogContent $originalChangelogContent
        }
        throw
    }

    Write-Host "Release artifacts are ready in $distDirectory"

    if ($Publish) {
        if ($Bump -ne "none") {
            Invoke-CheckedCommand -Command "git" -Arguments @("add", "--", "version.properties", "CHANGELOG.md")
            Invoke-CheckedCommand -Command "git" -Arguments @("commit", "-m", "Release $tag")
        }
        Invoke-CheckedCommand -Command "git" -Arguments @("tag", "-a", $tag, "-m", "Release $tag")
        Invoke-CheckedCommand -Command "git" -Arguments @("push", "--atomic", "origin", "HEAD", $tag)
        Write-Host "Published $tag. GitHub Actions will create the GitHub Release."
    }
} finally {
    Pop-Location
}
