param(
    [string]$DatasetRoot = "external_datasets/android_onehand162_20f_features",
    [string]$ModelOut = "model",
    [string]$ReportsOut = "reports"
)

Write-Host "============================================================"
Write-Host "VoxGest 20-frame Research Pipeline"
Write-Host "Dataset: $DatasetRoot"
Write-Host "============================================================"

python - << 'PY'
from pathlib import Path
import numpy as np

root = Path(r"$DatasetRoot")
counts = {}
bad = []

for p in root.rglob("*.npy"):
    label = p.parent.name.upper()
    arr = np.load(p)
    counts[label] = counts.get(label, 0) + 1
    if arr.shape != (20, 162):
        bad.append((str(p), arr.shape))

print("Class counts:")
for k, v in sorted(counts.items()):
    print(f"  {k}: {v}")

print("Bad shape count:", len(bad))
for item in bad[:20]:
    print(item)

if bad:
    raise SystemExit("Stop: bad shapes found.")
PY

Write-Host "Dataset shape check passed."

if (Test-Path ".\scripts_ml\train_rd_tcn_20f.py") {
    python ".\scripts_ml\train_rd_tcn_20f.py" --dataset_root $DatasetRoot --model_out $ModelOut --reports_out $ReportsOut
} else {
    Write-Host "Missing scripts_ml\train_rd_tcn_20f.py"
    Write-Host "Create the training script first using the RD-TCN code."
}

if (Test-Path ".\scripts_ml\92_eval_confusion_npy.py") {
    python ".\scripts_ml\92_eval_confusion_npy.py" `
        --dataset_root $DatasetRoot `
        --model "$ModelOut\voxgest_rd_tcn_20x162_float16.tflite" `
        --labels "$ModelOut\class_labels_tcn_onehand162_20f.json" `
        --output_csv "$ReportsOut\confusion_20f_rd_tcn.csv"
} else {
    Write-Host "Missing scripts_ml\92_eval_confusion_npy.py"
}
