param(
    [string]$PackageRoot = $PSScriptRoot
)

$ErrorActionPreference = "Stop"

function Assert-SafeCPath([string]$PathValue) {
    $full = [System.IO.Path]::GetFullPath($PathValue)
    if ($full -match '^[dD]:') { throw "D: is prohibited: $full" }
    if ($full -notmatch '^[cC]:') { throw "Use a safe C: package path: $full" }
    return $full
}

$PackageRoot = Assert-SafeCPath $PackageRoot
if (-not (Test-Path -LiteralPath $PackageRoot -PathType Container)) {
    throw "Package root does not exist: $PackageRoot"
}

$required = @(
    'START_HERE.txt',
    'CHECKSUMS.sha256',
    'INSTALL_AND_BOOTSTRAP.ps1',
    'VERIFY_PACKAGE.ps1',
    'knowledge\project\APPDEV_START_HERE.md',
    'knowledge\project\APPDEV_CHATGPT_SOL_BOOTSTRAP.md',
    'knowledge\avatar\AGENTS_AVATAR.md',
    'knowledge\avatar\ACCEPTANCE_GATES.md',
    'knowledge\avatar\GITHUB_PROTOCOL.md',
    'knowledge\avatar\USER_FACING_VOCABULARY_PRIORITY.md',
    'knowledge\avatar\AVATAR_PRESENTATION_SPEC.md',
    'knowledge\avatar\ASTRA_LIVE_HANDOFF.md',
    'knowledge\recognition_context\CODEX_LIVE_HANDOFF.md',
    'knowledge\recognition_context\LATEST_RECOGNITION_STATUS_20260914.md',
    'knowledge\documentation\NEW_CHATGPT_PROJECT_BOOTSTRAP.md',
    'knowledge\documentation\PROJECT_HISTORY_JUNE_SEPT_2026.md',
    'knowledge\documentation\DOCUMENTATION_POLICY.md',
    'knowledge\documentation\ARCHITECTURE_DECISIONS.md',
    'knowledge\documentation\AVATAR_ARCHITECTURE_DECISIONS.md',
    'knowledge\prompts\ASTRA_FIRST_RUN_PROMPT.md',
    'knowledge\prompts\ASTRA_RESUME_PROMPT.md',
    'knowledge\prompts\APPDEV_ASTRA_BOOTSTRAP_PROMPT.md',
    'knowledge\prompts\APPDEV_ZIP_SETUP_PROMPT.md',
    'repo_state\branch.txt',
    'repo_state\head.txt',
    'repo_state\remote.txt',
    'repo_state\recent_commits.txt',
    'repo_state\status.txt',
    'local_asset_instructions\LOCAL_ASSET_EXPECTATIONS.md'
)

$missing = @()
foreach ($relative in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $PackageRoot $relative) -PathType Leaf)) {
        $missing += $relative
    }
}
if ($missing.Count -gt 0) {
    throw "Required package files are missing: $($missing -join ', ')"
}

$prohibitedExtensions = @(
    '.blend', '.blend1', '.glb', '.gltf', '.fbx', '.vrm', '.vroid',
    '.mp4', '.mov', '.avi', '.mkv', '.webm', '.wav', '.mp3',
    '.png', '.jpg', '.jpeg', '.gif', '.bmp', '.webp',
    '.apk', '.aab', '.jks', '.keystore', '.p12', '.pfx', '.pem',
    '.npy', '.npz', '.tflite', '.bin', '.onnx', '.h5', '.ckpt', '.pt', '.pth',
    '.zip', '.7z', '.rar'
)
$prohibitedPathPatterns = @(
    '(^|/)(datasets?|raw|videos?|build|\.gradle|node_modules|venv|\.venv|checkpoints?|caches?)(/|$)',
    '(^|/)(private_calibration_assets|purchased_avatar)(/|$)'
)

$allFiles = @(Get-ChildItem -LiteralPath $PackageRoot -File -Recurse)
$prohibited = @()
foreach ($file in $allFiles) {
    $relative = $file.FullName.Substring($PackageRoot.Length + 1).Replace('\', '/')
    $extension = $file.Extension.ToLowerInvariant()
    $badPath = $false
    foreach ($pattern in $prohibitedPathPatterns) {
        if ($relative -match $pattern) { $badPath = $true; break }
    }
    if (($prohibitedExtensions -contains $extension) -or $badPath) {
        $prohibited += $relative
    }
}
if ($prohibited.Count -gt 0) {
    throw "Prohibited binary/private/generated files found: $($prohibited -join ', ')"
}

$manifestPath = Join-Path $PackageRoot 'CHECKSUMS.sha256'
$manifestLines = @(Get-Content -LiteralPath $manifestPath | Where-Object { $_.Trim().Length -gt 0 })
if ($manifestLines.Count -eq 0) { throw 'Checksum manifest is empty.' }

$manifestEntries = @{}
foreach ($line in $manifestLines) {
    if ($line -notmatch '^(?<hash>[A-Fa-f0-9]{64})  (?<path>.+)$') {
        throw "Invalid checksum line: $line"
    }
    $relative = $Matches['path'].Replace('/', '\')
    if ($manifestEntries.ContainsKey($relative)) { throw "Duplicate checksum entry: $relative" }
    $manifestEntries[$relative] = $Matches['hash'].ToUpperInvariant()
}

$actualRelative = @(
    $allFiles |
        Where-Object { $_.FullName -ne $manifestPath } |
        ForEach-Object { $_.FullName.Substring($PackageRoot.Length + 1) }
)

foreach ($relative in $actualRelative) {
    if (-not $manifestEntries.ContainsKey($relative)) {
        throw "File is not covered by CHECKSUMS.sha256: $relative"
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $PackageRoot $relative)).Hash
    if ($actualHash -ne $manifestEntries[$relative]) {
        throw "Checksum mismatch: $relative"
    }
}

foreach ($relative in $manifestEntries.Keys) {
    if (-not (Test-Path -LiteralPath (Join-Path $PackageRoot $relative) -PathType Leaf)) {
        throw "Checksum references a missing file: $relative"
    }
}

if ($manifestEntries.Count -ne $actualRelative.Count) {
    throw "Checksum coverage count mismatch: manifest=$($manifestEntries.Count), files=$($actualRelative.Count)"
}

Write-Host 'PACKAGE_VERIFY=PASS'
Write-Host "PACKAGE_ROOT=$PackageRoot"
Write-Host "REQUIRED_FILES=$($required.Count)"
Write-Host "CHECKSUMMED_FILES=$($manifestEntries.Count)"
Write-Host 'PROHIBITED_FILES=0'
