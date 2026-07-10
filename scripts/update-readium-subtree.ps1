param(
    [string] $Remote = "readium",
    [string] $RemoteUrl = "https://github.com/readium/kotlin-toolkit.git",
    [string] $Branch = "develop"
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
    & git @args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($args -join ' ') failed with exit code $LASTEXITCODE."
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

$dirty = git status --porcelain
if ($dirty) {
    throw "Working tree is not clean. Commit or stash your changes before updating the Readium subtree."
}

$existingRemote = git remote
if ($existingRemote -notcontains $Remote) {
    git remote add $Remote $RemoteUrl
}

$env:GIT_HTTP_LOW_SPEED_LIMIT = "1024"
$env:GIT_HTTP_LOW_SPEED_TIME = "20"

Invoke-Git -c http.version=HTTP/1.1 fetch $Remote $Branch

$hasSubtreeMetadata = git log --grep="git-subtree-dir: readium" --format="%H" -1
if (-not $hasSubtreeMetadata) {
    throw @"
Readium was fetched, but the existing readium/ directory was not originally added with git subtree.
This repository needs a one-time adoption/replacement step before normal subtree pulls can work.

Reason: upstream readium/kotlin-toolkit stores the modules under its own readium/ subdirectory,
while this repository already has that subdirectory copied into local readium/.
"@
}

Invoke-Git -c http.version=HTTP/1.1 subtree pull --prefix=readium $Remote $Branch --squash

Write-Host "Readium subtree updated from $Remote/$Branch."
