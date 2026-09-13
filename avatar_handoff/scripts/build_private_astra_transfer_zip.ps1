param(
  [string]$RepoRoot = "C:\VOXGEST_ASTRA\LatestVoxGest",
  [string]$AvatarInputs = "C:\VOXGEST_AVATAR_INPUTS",
  [string]$OutputRoot = "C:\VOXGEST_AVATAR_TRANSFER",
  [switch]$IncludePrivateCalibrationAssets
)

$ErrorActionPreference = "Stop"

foreach ($p in @($RepoRoot,$AvatarInputs,$OutputRoot)) {
  if ($p -match '^[dD]:') { throw "D: is prohibited: $p" }
  if ($p -notmatch '^[cC]:') { throw "Use safe C: storage: $p" }
}

$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$bundle = Join-Path $OutputRoot "VOXGEST_ASTRA_TRANSFER_$stamp"
$zip = "$bundle.zip"
New-Item -ItemType Directory -Force -Path $bundle | Out-Null

# Always include the versioned GitHub handoff/instructions.
Copy-Item -LiteralPath (Join-Path $RepoRoot 'avatar_handoff') -Destination (Join-Path $bundle 'avatar_handoff') -Recurse

$repoRefs = @(
  'reports\ASTRA_LIVE_HANDOFF.md',
  'docs\AVATAR_ARCHITECTURE_DECISIONS.md'
)
foreach ($rel in $repoRefs) {
  $src = Join-Path $RepoRoot $rel
  if (Test-Path -LiteralPath $src) {
    $dest = Join-Path $bundle $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $dest -Parent) | Out-Null
    Copy-Item -LiteralPath $src -Destination $dest
  }
}

$includedPrivate = @()
if ($IncludePrivateCalibrationAssets) {
  if (-not (Test-Path -LiteralPath $AvatarInputs)) {
    throw "AvatarInputs does not exist: $AvatarInputs"
  }

  # Copy only explicitly recognized calibration inputs. This archive is PRIVATE and must not be committed.
  $patterns = @(
    'purchased_avatar.blend',
    'voxgest_avatar_B32_CORE3_RC2.glb',
    'animation_manifest.json',
    'verification_summary.json',
    'blender_fsl_retarget.py',
    'bone_map.json',
    'retarget_config.json',
    '*retarget_general_B32_release_candidate_v1*'
  )

  $privateDest = Join-Path $bundle 'PRIVATE_CALIBRATION_ASSETS'
  New-Item -ItemType Directory -Force -Path $privateDest | Out-Null

  foreach ($pat in $patterns) {
    Get-ChildItem -LiteralPath $AvatarInputs -Filter $pat -File -Recurse -ErrorAction SilentlyContinue |
      ForEach-Object {
        $name = $_.Name
        $target = Join-Path $privateDest $name
        if (Test-Path -LiteralPath $target) {
          $target = Join-Path $privateDest ("{0}_{1}{2}" -f $_.BaseName,([guid]::NewGuid().ToString('N').Substring(0,8)),$_.Extension)
        }
        Copy-Item -LiteralPath $_.FullName -Destination $target
        $includedPrivate += $_.FullName
      }
  }
}

# Build a checksum manifest before compression.
$manifest = Join-Path $bundle 'TRANSFER_CHECKSUMS.sha256'
Get-ChildItem -LiteralPath $bundle -File -Recurse |
  Where-Object { $_.FullName -ne $manifest } |
  Sort-Object FullName |
  ForEach-Object {
    $h = Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName
    $relative = $_.FullName.Substring($bundle.Length + 1)
    "$($h.Hash)  $relative"
  } | Set-Content -Encoding UTF8 -LiteralPath $manifest

@"
VOXGEST ASTRA PRIVATE TRANSFER
Created=$((Get-Date).ToString('o'))
Repo=$RepoRoot
PrivateAssetsIncluded=$($IncludePrivateCalibrationAssets.IsPresent)
PRIVATE: Do not commit this ZIP or PRIVATE_CALIBRATION_ASSETS to GitHub.
Old D: workspace must not be used.
"@ | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $bundle 'READ_ME_FIRST.txt')

if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -LiteralPath $bundle -DestinationPath $zip -CompressionLevel Optimal

Write-Host "ZIP=$zip"
Write-Host "BYTES=$((Get-Item -LiteralPath $zip).Length)"
Write-Host "SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash)"
if ($IncludePrivateCalibrationAssets) {
  Write-Host "PRIVATE_ASSET_SOURCE_COUNT=$($includedPrivate.Count)"
  Write-Warning 'This ZIP may contain licensed/private Avatar assets. Transfer it directly to the app developer. NEVER commit it.'
} else {
  Write-Host 'Instruction-only ZIP created. Re-run with -IncludePrivateCalibrationAssets if a private direct-transfer package is needed.'
}