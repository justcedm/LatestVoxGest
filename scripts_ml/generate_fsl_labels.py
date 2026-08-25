# generate_fsl_labels.py
import csv, json, os

FSL_ROOT   = r"D:\BSIT 3RD YEAR\New VovGest\FSL-105 A dataset for recognizing 105 Filipino sign language videos\FSL-105 A dataset for recognizing 105 Filipino sign language videos"
LABELS_CSV = os.path.join(FSL_ROOT, "labels.csv")
OUTPUT     = r"D:\BSIT 3RD YEAR\New VovGest\model\class_labels_fsl.json"

def sanitize(label):
    return label.strip().upper().replace(" ", "_").replace("'", "").replace("/", "_")

label_map = {}
with open(LABELS_CSV, newline='', encoding='utf-8-sig') as f:
    reader = csv.DictReader(f)
    for row in reader:
        folder_name = sanitize(row['label'])
        label_map[folder_name] = int(row['id'])

with open(OUTPUT, 'w') as f:
    json.dump(label_map, f, indent=2)

print(f"Saved {len(label_map)} labels to {OUTPUT}")
for name, idx in list(label_map.items())[:10]:
    print(f"  {idx}: {name}")
