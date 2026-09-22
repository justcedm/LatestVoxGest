from pathlib import Path
import numpy as np

root = Path("external_datasets/android_onehand162_20f_features")
bad = []
count = 0
counts = {}

for p in root.rglob("*.npy"):
    label = p.parent.name.upper()
    arr = np.load(p)
    count += 1
    counts[label] = counts.get(label, 0) + 1
    if arr.shape != (20, 162):
        bad.append((str(p), arr.shape))

print("Total .npy:", count)
print("Class counts:")
for k, v in sorted(counts.items()):
    print(f"  {k}: {v}")

print("Bad shape:", len(bad))
for item in bad[:20]:
    print(item)

if count == 0:
    print("No .npy files found yet.")
elif bad:
    raise SystemExit("Stop: bad shapes found.")
else:
    print("All .npy files are valid shape (20, 162).")
