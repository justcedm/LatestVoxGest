param(
  [string]$RepoRoot = "C:\VOXGEST_ASTRA\LatestVoxGest",
  [string]$RemoteUrl = "https://github.com/justcedm/LatestVoxGest.git",
  [string]$BaseBranch = "recognition/recovery-20260912",
  [string]$AvatarBranch = "avatar/astra-calibration-20260914"
)

$ErrorActionPreference = "Stop"

function Assert-SafeC([string]$Path) {
  if ($Path -match '^[dD]:') { throw "D: is prohibited: $Path" }
  if ($Path -notmatch '^[cC]:') { throw "Use safe C: storage: $Path" }
}

Assert-SafeC $RepoRoot
New-Item -ItemType Directory -Force -Path (Split-Path $RepoRoot -Parent) | Out-Null

if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot '.git'))) {
  git clone $RemoteUrl $RepoRoot
  if ($LASTEXITCODE -ne 0) { throw 'git clone failed' }
}

Push-Location $RepoRoot
try {
  git fetch origin --prune
  if ($LASTEXITCODE -ne 0) { throw 'git fetch failed' }

  $dirty = git status --porcelain
  if ($dirty) {
    Write-Host $dirty
    throw 'Worktree is dirty. Review before switching branches.'
  }

  git rev-parse --verify "origin/$BaseBranch" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Missing origin/$BaseBranch" }

  $remoteAvatar = git ls-remote --heads origin $AvatarBranch
  if ($remoteAvatar) {
    git show-ref --verify --quiet "refs/heads/$AvatarBranch"
    if ($LASTEXITCODE -eq 0) {
      git switch $AvatarBranch
      git pull --ff-only
    } else {
      git switch --track "origin/$AvatarBranch"
    }
  } else {
    git switch --detach "origin/$BaseBranch"
    git switch -c $AvatarBranch
    git push -u origin $AvatarBranch
  }

  Write-Host "BRANCH=$(git branch --show-current)"
  Write-Host "HEAD=$(git rev-parse HEAD)"
  git status --short
}
finally {
  Pop-Location
}