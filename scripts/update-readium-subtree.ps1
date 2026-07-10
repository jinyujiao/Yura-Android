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

$lastSubtreeCommit = git log --grep="git-subtree-dir: readium" --format="%H" -1
if (-not $lastSubtreeCommit) {
    throw "No Readium subtree metadata found. Run the one-time subtree adoption before updating."
}

$lastSubtreeMessage = git log -1 --format="%B" $lastSubtreeCommit
$previousSplit = ($lastSubtreeMessage | Select-String "git-subtree-split: ([0-9a-f]+)").Matches.Groups[1].Value
if (-not $previousSplit) {
    throw "Could not find the previous Readium subtree split commit in $lastSubtreeCommit."
}

$upstreamTree = git rev-parse "FETCH_HEAD:readium"
$previousTree = git rev-parse "$previousSplit^{tree}"
if ($upstreamTree -eq $previousTree) {
    Write-Host "Readium subtree is already up to date with $Remote/$Branch."
    exit 0
}

$safeBranch = $Branch -replace "[^A-Za-z0-9._-]", "-"
$splitBranch = "readium-upstream-$safeBranch-split"
$message = "Synthetic Readium split from $Remote/$Branch"
$newSplit = $message | git commit-tree $upstreamTree -p $previousSplit
if ($LASTEXITCODE -ne 0) {
    throw "Could not create synthetic Readium split commit."
}

Invoke-Git branch -f $splitBranch $newSplit
Invoke-Git -c http.version=HTTP/1.1 subtree pull --prefix=readium . $splitBranch --squash -m "Update Readium subtree from $Remote/$Branch"

Write-Host "Readium subtree updated from $Remote/$Branch."
