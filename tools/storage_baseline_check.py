"""Read-only APK asset, FSL105 mapping and TFLite readability migration checks."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import zipfile
import tensorflow as tf


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('--baseline-apk', type=Path, required=True)
    parser.add_argument('--new-apk', type=Path, required=True)
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--fsl105', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    with zipfile.ZipFile(args.baseline_apk) as old, zipfile.ZipFile(args.new_apk) as new:
        old_names = {name for name in old.namelist() if name.startswith('assets/') or name.startswith('res/raw/')}
        new_names = {name for name in new.namelist() if name.startswith('assets/') or name.startswith('res/raw/')}
        if old_names != new_names:
            raise ValueError('Packaged asset/resource name set changed')
        apk_rows = []
        for name in sorted(old_names):
            old_hash, new_hash = digest(old.read(name)), digest(new.read(name))
            if old_hash != new_hash:
                raise ValueError(f'Packaged resource changed: {name}')
            apk_rows.append(dict(name=name, sha256=new_hash, equal=True))
        if old.testzip() or new.testzip():
            raise ValueError('APK ZIP member CRC failure')
    models = []
    for path in sorted(args.assets.rglob('*.tflite')):
        interpreter = tf.lite.Interpreter(model_path=str(path), num_threads=1)
        interpreter.allocate_tensors()
        models.append(dict(name=path.relative_to(args.assets).as_posix(), sha256=digest(path.read_bytes()),
                           input=[item['shape'].tolist() for item in interpreter.get_input_details()],
                           output=[item['shape'].tolist() for item in interpreter.get_output_details()],
                           readability='ALLOCATE_PASS_NOT_PHYSICAL_INFERENCE'))
    with (args.fsl105 / 'labels.csv').open(encoding='utf-8-sig', newline='') as stream:
        labels = list(csv.DictReader(stream))
    by_id = {row['id']: row for row in labels}
    if len(labels) != 105 or len(by_id) != 105:
        raise ValueError('FSL105 label IDs not unique105')
    mapped, seen = [], set()
    for split in ('train', 'test'):
        with (args.fsl105 / f'{split}.csv').open(encoding='utf-8-sig', newline='') as stream:
            for row in csv.DictReader(stream):
                authority = by_id[row['id_label']]
                if row['label'] != authority['label'] or row['category'] != authority['category']:
                    raise ValueError('FSL105 CSV label/category mapping mismatch')
                relative = Path(row['vid_path'].replace('\\', '/'))
                path = args.fsl105 / 'clips' / relative
                if not path.is_file() or path.resolve() in seen:
                    raise ValueError('Missing or repeated FSL105 clip path')
                seen.add(path.resolve())
                mapped.append(dict(published_split=split, csv_path=row['vid_path'],
                                   actual_relative_path=path.relative_to(args.fsl105).as_posix(),
                                   id=row['id_label'], label=authority['label'], category=authority['category']))
    actual = {path.resolve() for path in (args.fsl105 / 'clips').rglob('*.MOV')}
    if len(mapped) != 2130 or seen != actual:
        raise ValueError('FSL105 CSV/actual video set mismatch')
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(dict(status='PASS', apk_asset_count=len(apk_rows), apk_assets=apk_rows,
                                         models=models, fsl105_labels=105, fsl105_mapped_clips=len(mapped),
                                         source_csv_hashes={name: digest((args.fsl105 / name).read_bytes()) for name in ('labels.csv', 'train.csv', 'test.csv')}), indent=2), encoding='utf-8')
    with args.report.with_name('FSL105_PATH_RESOLUTION.csv').open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(mapped[0]))
        writer.writeheader()
        writer.writerows(mapped)
    print(f'BASELINE_PASS packaged_assets={len(apk_rows)} tflite_allocated={len(models)} fsl105_clips={len(mapped)}')


if __name__ == '__main__':
    main()
