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
    if ($Publish) {
        $workingTree = git status --porcelain
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to inspect the Git working tree."
        }
        if ($workingTree) {
            throw "Publishing requires a clean Git working tree. Commit the application changes first."
        }
    }

    $originalVersionContent = Get-Content -LiteralPath $versionFile -Raw
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
    if ($Bump -ne "none") {
        Write-AppVersion -Code $nextVersionCode -Name $nextVersionName
        Write-Host "Version: $($currentVersion.Name) ($($currentVersion.Code)) -> $nextVersionName ($nextVersionCode)"
    } else {
        Write-Host "Building current version: $nextVersionName ($nextVersionCode)"
    }

    try {
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
    } catch {
        [System.IO.File]::WriteAllText($versionFile, $originalVersionContent, $utf8NoBom)
        throw
    }

    Write-Host "Release artifacts are ready in $distDirectory"

    if ($Publish) {
        $tag = "v$nextVersionName"
        git rev-parse --quiet --verify "refs/tags/$tag" *> $null
        if ($LASTEXITCODE -eq 0) {
            throw "Tag already exists locally: $tag"
        }

        if ($Bump -ne "none") {
            Invoke-CheckedCommand -Command "git" -Arguments @("add", "--", "version.properties")
            Invoke-CheckedCommand -Command "git" -Arguments @("commit", "-m", "Release $tag")
        }
        Invoke-CheckedCommand -Command "git" -Arguments @("tag", "-a", $tag, "-m", "Release $tag")
        Invoke-CheckedCommand -Command "git" -Arguments @("push", "origin", "HEAD")
        Invoke-CheckedCommand -Command "git" -Arguments @("push", "origin", $tag)
        Write-Host "Published $tag. GitHub Actions will create the GitHub Release."
    }
} finally {
    Pop-Location
}
