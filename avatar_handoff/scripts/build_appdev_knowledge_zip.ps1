param(
    [string]$RepoRoot = 'C:\VOXGEST_APPDEV\LatestVoxGest',
    [string]$OutRoot = 'C:\VOXGEST_HANDOFF'
)

$ErrorActionPreference = 'Stop'
$AvatarBranch = 'avatar/astra-calibration-20260914'
$RemoteUrl = 'https://github.com/justcedm/LatestVoxGest.git'

function Assert-SafeCPath([string]$PathValue) {
    $full = [System.IO.Path]::GetFullPath($PathValue)
    if ($full -match '^[dD]:') { throw "D: is prohibited for VoxGest handoff work: $full" }
    if ($full -notmatch '^[cC]:') { throw "Use a safe C: path: $full" }
    return $full.TrimEnd('\')
}

$RepoRoot = Assert-SafeCPath $RepoRoot
$OutRoot = Assert-SafeCPath $OutRoot
if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot '.git') -PathType Container)) {
    throw "Git repository not found: $RepoRoot"
}

Push-Location $RepoRoot
try {
    $branch = (git branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read current branch.' }
    if ($branch -ne $AvatarBranch) {
        throw "Build the takeover only from $AvatarBranch. Current=$branch"
    }

    $remote = (git remote get-url origin).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read origin.' }
    if ($remote.TrimEnd('/') -ne $RemoteUrl.TrimEnd('/')) {
        throw "Unexpected origin. Expected=$RemoteUrl Actual=$remote"
    }

    $head = (git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read HEAD.' }
    $status = @(git status --short)

    New-Item -ItemType Directory -Force -Path $OutRoot | Out-Null
    $bundleRoot = Join-Path $OutRoot 'VOXGEST_APPDEV_TAKEOVER'
    $zipPath = Join-Path $OutRoot 'VOXGEST_APPDEV_TAKEOVER.zip'

    $bundleFull = [System.IO.Path]::GetFullPath($bundleRoot).TrimEnd('\')
    $outFull = [System.IO.Path]::GetFullPath($OutRoot).TrimEnd('\')
    if ([System.IO.Path]::GetDirectoryName($bundleFull) -ne $outFull) {
        throw "Unsafe staging target: $bundleFull"
    }
    if ($bundleFull -eq $outFull) { throw 'Staging target cannot equal output root.' }

    if (Test-Path -LiteralPath $bundleFull) {
        Remove-Item -LiteralPath $bundleFull -Recurse -Force
    }
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }

    foreach ($directory in @(
        'knowledge\project',
        'knowledge\avatar',
        'knowledge\avatar\reference_docs',
        'knowledge\avatar\runtime_source',
        'knowledge\avatar\scripts',
        'knowledge\recognition_context',
        'knowledge\documentation',
        'knowledge\prompts',
        'repo_state',
        'local_asset_instructions'
    )) {
        New-Item -ItemType Directory -Force -Path (Join-Path $bundleFull $directory) | Out-Null
    }

    $copyMap = @(
        @{ Source='avatar_handoff\APPDEV_START_HERE.md'; Dest='knowledge\project\APPDEV_START_HERE.md' },
        @{ Source='avatar_handoff\APPDEV_CHATGPT_SOL_BOOTSTRAP.md'; Dest='knowledge\project\APPDEV_CHATGPT_SOL_BOOTSTRAP.md' },
        @{ Source='avatar_handoff\README.md'; Dest='knowledge\project\AVATAR_HANDOFF_README.md' },
        @{ Source='avatar_handoff\AGENTS_AVATAR.md'; Dest='knowledge\avatar\AGENTS_AVATAR.md' },
        @{ Source='avatar_handoff\ACCEPTANCE_GATES.md'; Dest='knowledge\avatar\ACCEPTANCE_GATES.md' },
        @{ Source='avatar_handoff\GITHUB_PROTOCOL.md'; Dest='knowledge\avatar\GITHUB_PROTOCOL.md' },
        @{ Source='avatar_handoff\USER_FACING_VOCABULARY_PRIORITY.md'; Dest='knowledge\avatar\USER_FACING_VOCABULARY_PRIORITY.md' },
        @{ Source='avatar_handoff\AVATAR_PRESENTATION_SPEC.md'; Dest='knowledge\avatar\AVATAR_PRESENTATION_SPEC.md' },
        @{ Source='reports\ASTRA_LIVE_HANDOFF.md'; Dest='knowledge\avatar\ASTRA_LIVE_HANDOFF.md' },
        @{ Source='docs\AVATAR_PIPELINE.md'; Dest='knowledge\avatar\reference_docs\AVATAR_PIPELINE.md' },
        @{ Source='docs\AVATURN_AVATAR_INTEGRATION.md'; Dest='knowledge\avatar\reference_docs\AVATURN_AVATAR_INTEGRATION.md' },
        @{ Source='android_dry_run\reports\CORE3_AVATAR_ANDROID_INTEGRATION_20260909.md'; Dest='knowledge\avatar\reference_docs\CORE3_AVATAR_ANDROID_INTEGRATION_20260909.md' },
        @{ Source='android_dry_run\reports\LISTEN_AVATAR_FINAL_DEVICE_QA_20260910.md'; Dest='knowledge\avatar\reference_docs\LISTEN_AVATAR_FINAL_DEVICE_QA_20260910.md' },
        @{ Source='android_dry_run\app\src\main\assets\avatar\core3\animation_manifest.json'; Dest='knowledge\avatar\runtime_source\animation_manifest.json' },
        @{ Source='android_dry_run\app\src\main\java\com\voxgest\app\avatar\Core3AvatarCatalog.kt'; Dest='knowledge\avatar\runtime_source\Core3AvatarCatalog.kt' },
        @{ Source='android_dry_run\app\src\main\java\com\voxgest\app\avatar\Core3AvatarRuntimeController.kt'; Dest='knowledge\avatar\runtime_source\Core3AvatarRuntimeController.kt' },
        @{ Source='android_dry_run\app\src\main\java\com\voxgest\app\avatar\Core3FilamentHostView.kt'; Dest='knowledge\avatar\runtime_source\Core3FilamentHostView.kt' },
        @{ Source='android_dry_run\app\src\main\java\com\voxgest\app\avatar\Core3ListenTranscriptResolver.kt'; Dest='knowledge\avatar\runtime_source\Core3ListenTranscriptResolver.kt' },
        @{ Source='android_dry_run\app\src\test\java\com\voxgest\app\avatar\Core3AvatarRuntimeControllerTest.kt'; Dest='knowledge\avatar\runtime_source\Core3AvatarRuntimeControllerTest.kt' },
        @{ Source='android_dry_run\app\src\test\java\com\voxgest\app\avatar\Core3ListenTranscriptResolverTest.kt'; Dest='knowledge\avatar\runtime_source\Core3ListenTranscriptResolverTest.kt' },
        @{ Source='avatar_handoff\scripts\bootstrap_astra.ps1'; Dest='knowledge\avatar\scripts\bootstrap_astra.ps1' },
        @{ Source='avatar_handoff\scripts\inventory_avatar_assets.ps1'; Dest='knowledge\avatar\scripts\inventory_avatar_assets.ps1' },
        @{ Source='avatar_handoff\scripts\build_appdev_knowledge_zip.ps1'; Dest='knowledge\avatar\scripts\build_appdev_knowledge_zip.ps1' },
        @{ Source='reports\CODEX_LIVE_HANDOFF.md'; Dest='knowledge\recognition_context\CODEX_LIVE_HANDOFF.md' },
        @{ Source='avatar_handoff\LATEST_RECOGNITION_STATUS_20260914.md'; Dest='knowledge\recognition_context\LATEST_RECOGNITION_STATUS_20260914.md' },
        @{ Source='docs\NEW_CHATGPT_PROJECT_BOOTSTRAP.md'; Dest='knowledge\documentation\NEW_CHATGPT_PROJECT_BOOTSTRAP.md' },
        @{ Source='docs\PROJECT_HISTORY_JUNE_SEPT_2026.md'; Dest='knowledge\documentation\PROJECT_HISTORY_JUNE_SEPT_2026.md' },
        @{ Source='docs\DOCUMENTATION_POLICY.md'; Dest='knowledge\documentation\DOCUMENTATION_POLICY.md' },
        @{ Source='docs\ARCHITECTURE_DECISIONS.md'; Dest='knowledge\documentation\ARCHITECTURE_DECISIONS.md' },
        @{ Source='docs\AVATAR_ARCHITECTURE_DECISIONS.md'; Dest='knowledge\documentation\AVATAR_ARCHITECTURE_DECISIONS.md' },
        @{ Source='reports\APPDEV_ASTRA_TAKEOVER_AUDIT_20260914.md'; Dest='knowledge\documentation\APPDEV_ASTRA_TAKEOVER_AUDIT_20260914.md' },
        @{ Source='avatar_handoff\ASTRA_FIRST_RUN_PROMPT.md'; Dest='knowledge\prompts\ASTRA_FIRST_RUN_PROMPT.md' },
        @{ Source='avatar_handoff\ASTRA_RESUME_PROMPT.md'; Dest='knowledge\prompts\ASTRA_RESUME_PROMPT.md' },
        @{ Source='avatar_handoff\APPDEV_ASTRA_BOOTSTRAP_PROMPT.md'; Dest='knowledge\prompts\APPDEV_ASTRA_BOOTSTRAP_PROMPT.md' },
        @{ Source='avatar_handoff\APPDEV_ZIP_SETUP_PROMPT.md'; Dest='knowledge\prompts\APPDEV_ZIP_SETUP_PROMPT.md' },
        @{ Source='avatar_handoff\LOCAL_ASSET_EXPECTATIONS.md'; Dest='local_asset_instructions\LOCAL_ASSET_EXPECTATIONS.md' },
        @{ Source='avatar_handoff\package_templates\START_HERE.txt'; Dest='START_HERE.txt' },
        @{ Source='avatar_handoff\package_templates\INSTALL_AND_BOOTSTRAP.ps1'; Dest='INSTALL_AND_BOOTSTRAP.ps1' },
        @{ Source='avatar_handoff\package_templates\VERIFY_PACKAGE.ps1'; Dest='VERIFY_PACKAGE.ps1' }
    )

    foreach ($item in $copyMap) {
        $source = Join-Path $RepoRoot $item.Source
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Required source is missing: $($item.Source)"
        }
        $destination = Join-Path $bundleFull $item.Dest
        New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Force
    }

    Set-Content -LiteralPath (Join-Path $bundleFull 'repo_state\branch.txt') -Encoding ASCII -Value $branch
    Set-Content -LiteralPath (Join-Path $bundleFull 'repo_state\head.txt') -Encoding ASCII -Value $head
    Set-Content -LiteralPath (Join-Path $bundleFull 'repo_state\remote.txt') -Encoding UTF8 -Value $remote
    git log --date=iso-strict --pretty=format:'%H`t%ad`t%s' -n 50 |
        Set-Content -LiteralPath (Join-Path $bundleFull 'repo_state\recent_commits.txt') -Encoding UTF8
    if ($status.Count -eq 0) {
        Set-Content -LiteralPath (Join-Path $bundleFull 'repo_state\status.txt') -Encoding ASCII -Value 'CLEAN'
    } else {
        $status | Set-Content -LiteralPath (Join-Path $bundleFull 'repo_state\status.txt') -Encoding UTF8
    }

    $manifestPath = Join-Path $bundleFull 'CHECKSUMS.sha256'
    $manifestLines = @(
        Get-ChildItem -LiteralPath $bundleFull -File -Recurse |
            Where-Object { $_.FullName -ne $manifestPath } |
            Sort-Object FullName |
            ForEach-Object {
                $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash
                $relative = $_.FullName.Substring($bundleFull.Length + 1).Replace('\', '/')
                "$hash  $relative"
            }
    )
    $manifestLines | Set-Content -LiteralPath $manifestPath -Encoding ASCII

    & (Join-Path $bundleFull 'VERIFY_PACKAGE.ps1') -PackageRoot $bundleFull
    Compress-Archive -LiteralPath $bundleFull -DestinationPath $zipPath -CompressionLevel Optimal

    Write-Host "KNOWLEDGE_ZIP=$zipPath"
    Write-Host "ZIP_SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $zipPath).Hash)"
    Write-Host "PACKAGE_ROOT=$bundleFull"
    Write-Host "BRANCH=$branch"
    Write-Host "HEAD=$head"
    Write-Host "SOURCE_FILES=$($copyMap.Count)"
    Write-Host "CHECKSUMMED_FILES=$($manifestLines.Count)"
    Write-Host 'PRIVATE_ASSETS_INCLUDED=NO'
}
finally {
    Pop-Location
}
