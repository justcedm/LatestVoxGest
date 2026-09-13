param(
  [string]$RepoRoot = "C:\VOXGEST_ASTRA\LatestVoxGest",
  [string]$AvatarInputs = "C:\VOXGEST_AVATAR_INPUTS",
  [string]$AvatarWork = "C:\VOXGEST_AVATAR_WORK\20260914"
)

$ErrorActionPreference = "Stop"

foreach ($p in @($RepoRoot,$AvatarInputs,$AvatarWork)) {
  if ($p -match '^[dD]:') { throw "D: is prohibited: $p" }
  if ($p -notmatch '^[cC]:') { throw "Use safe C: storage: $p" }
}

New-Item -ItemType Directory -Force -Path $AvatarInputs,$AvatarWork | Out-Null

$roots = @()
foreach ($r in @($RepoRoot,$AvatarInputs,"C:\VOXGEST_RECOVERY_20260910","C:\VOXGEST_ASTRA")) {
  if (Test-Path -LiteralPath $r) { $roots += $r }
}

$patterns = @(
  "purchased_avatar.blend",
  "voxgest_avatar_B32_CORE3_RC2.glb",
  "animation_manifest.json",
  "verification_summary.json",
  "blender_fsl_retarget.py",
  "bone_map.json",
  "retarget_config.json",
  "*retarget_general_B32_release_candidate_v1*",
  "*CORE3*",
  "*raw225*"
)

$rows = New-Object System.Collections.Generic.List[object]
foreach ($root in $roots) {
  foreach ($pat in $patterns) {
    Get-ChildItem -LiteralPath $root -Filter $pat -File -Recurse -ErrorAction SilentlyContinue |
      ForEach-Object {
        $hash = $null
        if ($_.Length -lt 150MB) {
          try { $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash } catch {}
        }
        $rows.Add([pscustomobject]@{
          Root=$root
          Name=$_.Name
          FullName=$_.FullName
          Bytes=$_.Length
          SHA256=$hash
          Modified=$_.LastWriteTime.ToString('s')
        })
      }
  }
}

$rows = $rows | Sort-Object FullName -Unique
$outCsv = Join-Path $AvatarWork 'ASTRA_LOCAL_ASSET_INVENTORY.csv'
$rows | Export-Csv -NoTypeInformation -Encoding UTF8 -LiteralPath $outCsv

Write-Host "Inventory=$outCsv"
$rows | Format-Table Name,Bytes,FullName -AutoSize
Write-Host 'No assets were copied or committed by this script.'