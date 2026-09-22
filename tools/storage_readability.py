"""Non-training migration checks: full video decode, safe array/container parsing.

Outputs are private operational manifests, not training features or evaluation scores.
"""
from __future__ import annotations
import argparse
import concurrent.futures
import csv
import json
from pathlib import Path
import zipfile
import cv2
import numpy as np


def inspect_file(path_string):
    path = Path(path_string)
    result = dict(path=str(path), extension=path.suffix.lower(), status='PASS', detail='')
    try:
        extension = path.suffix.lower()
        if extension in ('.mp4', '.mov', '.avi', '.mkv', '.webm'):
            cv2.setNumThreads(1)
            cap = cv2.VideoCapture(str(path))
            if not cap.isOpened():
                raise ValueError('Video decoder cannot open file')
            expected = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            fps = cap.get(cv2.CAP_PROP_FPS)
            decoded, shape = 0, None
            try:
                while True:
                    ok, frame = cap.read()
                    if not ok:
                        break
                    if frame is None or frame.size == 0:
                        raise ValueError('Empty decoded frame')
                    decoded += 1
                    shape = list(frame.shape)
            finally:
                cap.release()
            if decoded == 0 or (expected > 0 and decoded != expected):
                raise ValueError(f'Decode count mismatch decoded={decoded} advertised={expected}')
            result['detail'] = json.dumps(dict(decoded_frames=decoded, advertised_frames=expected, fps=fps, shape=shape))
        elif extension == '.npy':
            values = np.load(path, allow_pickle=False)
            finite = bool(np.isfinite(values).all()) if values.dtype.kind in 'fc' else True
            if not finite:
                raise ValueError('Array has NaN/Inf')
            result['detail'] = json.dumps(dict(shape=list(values.shape), dtype=str(values.dtype), finite=finite))
        elif extension == '.npz':
            with np.load(path, allow_pickle=False) as values:
                for key in values.files:
                    _ = values[key].shape
                result['detail'] = f'Arrays readable: {len(values.files)}'
        elif extension in ('.zip', '.apk'):
            with zipfile.ZipFile(path) as archive:
                bad = archive.testzip()
                if bad:
                    raise ValueError(f'CRC failure: {bad}')
                if extension == '.apk' and 'AndroidManifest.xml' not in archive.namelist():
                    raise ValueError('Missing AndroidManifest.xml')
                result['detail'] = f'ZIP central directory and all member CRCs PASS; entries={len(archive.infolist())}'
        elif extension == '.json':
            json.loads(path.read_text(encoding='utf-8-sig'))
            result['detail'] = 'JSON parse PASS'
        elif extension == '.csv':
            with path.open(encoding='utf-8-sig', newline='') as stream:
                rows = list(csv.reader(stream, strict=True))
            result['detail'] = f'CSV parse PASS; rows_including_header={len(rows)}'
        elif extension == '.tflite':
            with path.open('rb') as stream:
                prefix = stream.read(8)
            if prefix[4:8] != b'TFL3':
                raise ValueError('Missing TFLite FlatBuffer identifier')
            result['detail'] = 'TFL3 container identifier PASS; not an inference test'
        else:
            result['status'] = 'NOT_APPLICABLE'
    except Exception as error:
        result['status'] = 'FAIL'
        result['detail'] = f'{type(error).__name__}: {error}'
    return result


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('--root', type=Path, action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--workers', type=int, default=3)
    parser.add_argument('--videos-only', action='store_true')
    args = parser.parse_args()
    extensions = {'.mp4', '.mov', '.avi', '.mkv', '.webm'}
    if not args.videos_only:
        extensions.update({'.npy', '.npz', '.zip', '.apk', '.json', '.csv', '.tflite'})
    files = sorted({str(path) for root in args.root for path in ([root] if root.is_file() else root.rglob('*')) if path.is_file() and path.suffix.lower() in extensions})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    failures = 0
    with args.output.open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=['path', 'extension', 'status', 'detail'])
        writer.writeheader()
        with concurrent.futures.ProcessPoolExecutor(max_workers=args.workers) as pool:
            for index, result in enumerate(pool.map(inspect_file, files, chunksize=5), 1):
                writer.writerow(result)
                if result['status'] == 'FAIL':
                    failures += 1
                    print(json.dumps(result), flush=True)
                if index % 100 == 0:
                    stream.flush()
                    print(f'READABILITY {index}/{len(files)} failures={failures}', flush=True)
    print(f'READABILITY_COMPLETE files={len(files)} failures={failures}', flush=True)
    raise SystemExit(1 if failures else 0)


if __name__ == '__main__':
    main()
