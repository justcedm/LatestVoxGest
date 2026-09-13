param(
    [string]$RepoRoot = "C:\VOXGEST_ASTRA\LatestVoxGest",
    [string]$OutRoot = "C:\VOXGEST_HANDOFF"
)

$ErrorActionPreference = "Stop"

function Assert-SafeCPath([string]$PathValue) {
    if ($PathValue -match '^[dD]:') { throw "D: is prohibited for VoxGest handoff work: $PathValue" }
    if ($PathValue -notmatch '^[cC]:') { throw "Use a safe C: path: $PathValue" }
}

Assert-SafeCPath $RepoRoot
Assert-SafeCPath $OutRoot

if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot ".git"))) {
    throw "Git repository not found at $RepoRoot. Clone LatestVoxGest first."
}

Push-Location $RepoRoot

try {
    Write-Host "=== VOXGEST APPDEV KNOWLEDGE HANDOFF ==="

    git fetch origin --prune
    if ($LASTEXITCODE -ne 0) { throw "git fetch failed" }

    $branch = git branch --show-current
    if ($branch -ne "avatar/astra-calibration-20260914") {
        Write-Host "Current branch: $branch"
        Write-Host "Switching to avatar/astra-calibration-20260914 ..."
        $dirty = git status --porcelain
        if ($dirty) { throw "Worktree is dirty. Review/commit/stash before switching branches." }
        git switch avatar/astra-calibration-20260914 2>$null
        if ($LASTEXITCODE -ne 0) {
            git switch --track origin/avatar/astra-calibration-20260914
        }
        if ($LASTEXITCODE -ne 0) { throw "Unable to switch to Astra branch" }
    }

    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $bundleRoot = Join-Path $OutRoot "VOXGEST_APPDEV_ASTRA_KNOWLEDGE_$stamp"
    $repoPack = Join-Path $bundleRoot "repo_knowledge"
    New-Item -ItemType Directory -Force -Path $repoPack | Out-Null

    $requiredFiles = @(
        "avatar_handoff/README.md",
        "avatar_handoff/AGENTS_AVATAR.md",
        "avatar_handoff/ASTRA_FIRST_RUN_PROMPT.md",
        "avatar_handoff/ACCEPTANCE_GATES.md",
        "avatar_handoff/GITHUB_PROTOCOL.md",
        "avatar_handoff/USER_FACING_VOCABULARY_PRIORITY.md",
        "avatar_handoff/AVATAR_PRESENTATION_SPEC.md",
        "docs/NEW_CHATGPT_PROJECT_BOOTSTRAP.md",
        "docs/PROJECT_HISTORY_JUNE_SEPT_2026.md",
        "docs/DOCUMENTATION_POLICY.md",
        "docs/ARCHITECTURE_DECISIONS.md",
        "docs/AVATAR_ARCHITECTURE_DECISIONS.md",
        "reports/ASTRA_LIVE_HANDOFF.md",
        "reports/CODEX_LIVE_HANDOFF.md"
    )

    $optionalNamePatterns = @(
        "*AVATAR*",
        "*CORE3*",
        "*FILAMENT*",
        "*FSL105*",
        "*FULLSIGN225*",
        "*ANDROID*HANDOFF*"
    )

    $copied = New-Object System.Collections.Generic.List[string]
    $missing = New-Object System.Collections.Generic.List[string]

    function Copy-RepoFile([string]$RelativePath) {
        $src = Join-Path $RepoRoot ($RelativePath -replace '/', '\')
        if (-not (Test-Path -LiteralPath $src -PathType Leaf)) {
            $script:missing.Add($RelativePath)
            return
        }
        $dest = Join-Path $repoPack ($RelativePath -replace '/', '\')
        New-Item -ItemType Directory -Force -Path (Split-Path $dest -Parent) | Out-Null
        Copy-Item -LiteralPath $src -Destination $dest -Force
        $script:copied.Add($RelativePath)
    }

    foreach ($f in $requiredFiles) { Copy-RepoFile $f }

    # Pull additional small text/code evidence relevant to Avatar continuation.
    $allowedExt = @(".md", ".txt", ".json", ".csv", ".py", ".kt", ".kts", ".gradle", ".properties")
    $tracked = git ls-files
    foreach ($rel in $tracked) {
        $name = [System.IO.Path]::GetFileName($rel)
        $match = $false
        foreach ($p in $optionalNamePatterns) {
            if ($name -like $p) { $match = $true; break }
        }
        if (-not $match) { continue }
        $ext = [System.IO.Path]::GetExtension($rel).ToLowerInvariant()
        if ($allowedExt -notcontains $ext) { continue }
        $src = Join-Path $RepoRoot ($rel -replace '/', '\')
        if (-not (Test-Path -LiteralPath $src -PathType Leaf)) { continue }
        $size = (Get-Item -LiteralPath $src).Length
        if ($size -gt 5MB) { continue }
        if ($copied -contains $rel) { continue }
        Copy-RepoFile $rel
    }

    # Repository state snapshot so the receiving Codex knows exactly what it received.
    $meta = Join-Path $bundleRoot "repository_state"
    New-Item -ItemType Directory -Force -Path $meta | Out-Null

    git remote -v | Out-File -Encoding utf8 (Join-Path $meta "git_remote.txt")
    git branch -a -vv | Out-File -Encoding utf8 (Join-Path $meta "git_branches.txt")
    git status --short | Out-File -Encoding utf8 (Join-Path $meta "git_status.txt")
    git rev-parse HEAD | Out-File -Encoding ascii (Join-Path $meta "head_sha.txt")
    git log --date=iso-strict --pretty=format:"%H`t%ad`t%s" -n 100 | Out-File -Encoding utf8 (Join-Path $meta "recent_git_history.tsv")

    $manifest = @()
    foreach ($rel in ($copied | Sort-Object -Unique)) {
        $p = Join-Path $repoPack ($rel -replace '/', '\')
        if (Test-Path -LiteralPath $p) {
            $h = (Get-FileHash -Algorithm SHA256 -LiteralPath $p).Hash
            $manifest += "$h  $rel"
        }
    }
    $manifest | Out-File -Encoding ascii (Join-Path $bundleRoot "CHECKSUMS.sha256")

    @(
        "VoxGest App Developer / Astra Knowledge Bundle",
        "Created: $(Get-Date -Format o)",
        "Repository: https://github.com/justcedm/LatestVoxGest.git",
        "Branch: $(git branch --show-current)",
        "HEAD: $(git rev-parse HEAD)",
        "",
        "START HERE:",
        "1. repo_knowledge/docs/NEW_CHATGPT_PROJECT_BOOTSTRAP.md",
        "2. repo_knowledge/avatar_handoff/README.md",
        "3. repo_knowledge/avatar_handoff/ASTRA_FIRST_RUN_PROMPT.md",
        "",
        "This ZIP intentionally excludes purchased Avatar binaries, raw datasets, APKs and large evidence media.",
        "The app developer already owns the purchased Avatar source and should keep it outside Git."
    ) | Out-File -Encoding utf8 (Join-Path $bundleRoot "START_HERE.txt")

    if ($missing.Count -gt 0) {
        $missing | Sort-Object -Unique | Out-File -Encoding utf8 (Join-Path $bundleRoot "OPTIONAL_OR_MISSING_FILES.txt")
    }

    New-Item -ItemType Directory -Force -Path $OutRoot | Out-Null
    $zip = "$bundleRoot.zip"
    if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
    Compress-Archive -Path (Join-Path $bundleRoot "*") -DestinationPath $zip -CompressionLevel Optimal

    Write-Host ""
    Write-Host "KNOWLEDGE_ZIP=$zip"
    Write-Host "BRANCH=$(git branch --show-current)"
    Write-Host "HEAD=$(git rev-parse HEAD)"
    Write-Host "COPIED_FILES=$($copied.Count)"
    Write-Host "MISSING_EXPECTED=$($missing.Count)"
    Write-Host ""
    Write-Host "PASS: knowledge handoff created. Purchased/private Avatar source was not included."
}
finally {
    Pop-Location
}