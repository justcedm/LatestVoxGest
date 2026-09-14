param(
    [string]$PackageRoot = $PSScriptRoot,
    [string]$AppDevRoot = 'C:\VOXGEST_APPDEV',
    [string]$RepoRoot = 'C:\VOXGEST_APPDEV\LatestVoxGest',
    [string]$AvatarInputs = 'C:\VOXGEST_AVATAR_INPUTS',
    [string]$AvatarWork = 'C:\VOXGEST_AVATAR_WORK',
    [string]$KnowledgeRoot = 'C:\VOXGEST_APPDEV\KNOWLEDGE',
    [string]$EvidenceRoot = 'C:\VOXGEST_APPDEV\EVIDENCE',
    [string]$RemoteUrl = 'https://github.com/justcedm/LatestVoxGest.git',
    [string]$AvatarBranch = 'avatar/astra-calibration-20260914'
)

$ErrorActionPreference = 'Stop'

function Assert-SafeCPath([string]$PathValue) {
    $full = [System.IO.Path]::GetFullPath($PathValue)
    if ($full -match '^[dD]:') { throw "D: is prohibited: $full" }
    if ($full -notmatch '^[cC]:') { throw "Use a safe C: path: $full" }
    return $full
}

$PackageRoot = Assert-SafeCPath $PackageRoot
$AppDevRoot = Assert-SafeCPath $AppDevRoot
$RepoRoot = Assert-SafeCPath $RepoRoot
$AvatarInputs = Assert-SafeCPath $AvatarInputs
$AvatarWork = Assert-SafeCPath $AvatarWork
$KnowledgeRoot = Assert-SafeCPath $KnowledgeRoot
$EvidenceRoot = Assert-SafeCPath $EvidenceRoot

$verifyScript = Join-Path $PackageRoot 'VERIFY_PACKAGE.ps1'
if (-not (Test-Path -LiteralPath $verifyScript -PathType Leaf)) {
    throw "Package verifier is missing: $verifyScript"
}
& $verifyScript -PackageRoot $PackageRoot

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'Git for Windows is required and was not found on PATH.'
}
git --version
if ($LASTEXITCODE -ne 0) { throw 'Git executable check failed.' }

New-Item -ItemType Directory -Force -Path @(
    $AppDevRoot,
    $AvatarInputs,
    $AvatarWork,
    $KnowledgeRoot,
    $EvidenceRoot
) | Out-Null

if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot '.git') -PathType Container)) {
    if (Test-Path -LiteralPath $RepoRoot) {
        $existing = @(Get-ChildItem -LiteralPath $RepoRoot -Force)
        if ($existing.Count -gt 0) {
            throw "RepoRoot exists and is not an empty Git repository: $RepoRoot"
        }
    }
    git clone $RemoteUrl $RepoRoot
    if ($LASTEXITCODE -ne 0) { throw 'git clone failed.' }
}

Push-Location $RepoRoot
try {
    $actualRemote = (git remote get-url origin).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read origin remote.' }
    if ($actualRemote.TrimEnd('/') -ne $RemoteUrl.TrimEnd('/')) {
        throw "Unexpected origin remote. Expected=$RemoteUrl Actual=$actualRemote"
    }

    git fetch origin --prune
    if ($LASTEXITCODE -ne 0) { throw 'git fetch failed.' }

    $dirty = @(git status --porcelain)
    if ($dirty.Count -gt 0) {
        Write-Host ($dirty -join [Environment]::NewLine)
        throw 'Existing worktree is dirty. Preserve/review/commit it manually; the installer will not reset, discard, stash, switch, or overwrite it.'
    }

    git show-ref --verify --quiet "refs/remotes/origin/$AvatarBranch"
    if ($LASTEXITCODE -ne 0) { throw "Remote Avatar branch is missing: origin/$AvatarBranch" }

    git show-ref --verify --quiet "refs/heads/$AvatarBranch"
    if ($LASTEXITCODE -eq 0) {
        git switch $AvatarBranch
    } else {
        git switch --track "origin/$AvatarBranch"
    }
    if ($LASTEXITCODE -ne 0) { throw "Unable to switch to $AvatarBranch" }

    git pull --ff-only origin $AvatarBranch
    if ($LASTEXITCODE -ne 0) { throw 'Fast-forward repository update failed; inspect divergence manually.' }

    $branch = (git branch --show-current).Trim()
    if ($branch -ne $AvatarBranch) { throw "Wrong branch after setup: $branch" }

    $requiredRepoFiles = @(
        'avatar_handoff\APPDEV_START_HERE.md',
        'avatar_handoff\APPDEV_ASTRA_BOOTSTRAP_PROMPT.md',
        'avatar_handoff\APPDEV_CHATGPT_SOL_BOOTSTRAP.md',
        'avatar_handoff\ASTRA_FIRST_RUN_PROMPT.md',
        'avatar_handoff\ASTRA_RESUME_PROMPT.md',
        'avatar_handoff\scripts\inventory_avatar_assets.ps1',
        'reports\ASTRA_LIVE_HANDOFF.md'
    )
    $missingRepoFiles = @()
    foreach ($relative in $requiredRepoFiles) {
        if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $relative) -PathType Leaf)) {
            $missingRepoFiles += $relative
        }
    }
    if ($missingRepoFiles.Count -gt 0) {
        throw "Checked-out repository is missing required handoff files: $($missingRepoFiles -join ', ')"
    }

    Write-Host "BRANCH=$branch"
    Write-Host "HEAD=$((git rev-parse HEAD).Trim())"
    Write-Host "REMOTE=$actualRemote"
}
finally {
    Pop-Location
}

$knowledgeSource = Join-Path $PackageRoot 'knowledge'
Get-ChildItem -LiteralPath $knowledgeSource -Force |
    Copy-Item -Destination $KnowledgeRoot -Recurse -Force

$inventoryScript = Join-Path $RepoRoot 'avatar_handoff\scripts\inventory_avatar_assets.ps1'
$inventoryWork = Join-Path $AvatarWork '20260914'
& $inventoryScript -RepoRoot $RepoRoot -AvatarInputs $AvatarInputs -AvatarWork $inventoryWork

Write-Host 'BOOTSTRAP_STATUS=PASS'
Write-Host "KNOWLEDGE=$KnowledgeRoot"
Write-Host "AVATAR_INPUTS=$AvatarInputs"
Write-Host 'PURCHASED_AVATAR_COPIED=NO'
Write-Host ''
Write-Host 'NEXT STEP 1:'
Write-Host 'Open C:\VOXGEST_APPDEV\LatestVoxGest in ChatGPT Desktop / Codex.'
Write-Host ''
Write-Host 'NEXT STEP 2:'
Write-Host 'Paste the contents/instruction from:'
Write-Host 'avatar_handoff\APPDEV_ASTRA_BOOTSTRAP_PROMPT.md'
Write-Host ''
Write-Host 'NEXT STEP 3:'
Write-Host 'For the normal ChatGPT project conversation, use:'
Write-Host 'avatar_handoff\APPDEV_CHATGPT_SOL_BOOTSTRAP.md'
Write-Host ''
Write-Host 'NEXT STEP 4:'
Write-Host 'Astra must read reports\ASTRA_LIVE_HANDOFF.md before modifying anything.'
Write-Host ''
Write-Host 'Place or reference the developer-owned purchased Avatar under C:\VOXGEST_AVATAR_INPUTS or another explicit safe C: path. Do not copy it into Git.'
