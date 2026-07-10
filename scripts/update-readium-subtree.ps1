param(
    [string] $Remote = "readium",
    [string] $RemoteUrl = "https://github.com/readium/kotlin-toolkit.git",
    [string] $Branch = "develop"
)

$ErrorActionPreference = "Stop"

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

git fetch $Remote $Branch
git subtree pull --prefix=readium $Remote $Branch --squash

Write-Host "Readium subtree updated from $Remote/$Branch."
