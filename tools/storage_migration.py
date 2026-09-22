"""Copy-only, fail-closed storage audit. Private path plans/reports stay outside Git.

No delete/move/purge commands. Existing destination differences are never overwritten.
The source worktree is copied only as a separate archive, never used as the new clone.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import csv
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import time


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def tree(root: Path) -> dict[str, tuple[Path, int, int]]:
    if not root.exists():
        raise FileNotFoundError(f'Inventory source disappeared: {root}')
    items = {}
    candidates = [root] if root.is_file() else root.rglob('*')
    for path in candidates:
        info = path.lstat()
        if getattr(info, 'st_file_attributes', 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT:
            raise RuntimeError(f'Reparse point requires explicit review: {path}')
        if path.is_file():
            relative = path.name if root.is_file() else path.relative_to(root).as_posix()
            items[relative] = (path, info.st_size, info.st_mtime_ns)
    return items


def write_csv(path: Path, fields: list[str], rows) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def guarded_destination(path: Path, canonical: Path) -> None:
    if not path.is_absolute() or path.resolve() == canonical.resolve():
        raise RuntimeError('Destination must be a specific absolute descendant')
    if canonical.resolve() not in path.resolve().parents:
        raise RuntimeError(f'Destination escapes canonical root: {path}')
    for parent in (path, *path.parents):
        if parent.exists() and getattr(parent.lstat(), 'st_file_attributes', 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT:
            raise RuntimeError(f'Destination uses reparse point: {parent}')


def copy_job(entry, report: Path, canonical: Path):
    source, dest = Path(entry['source']), Path(entry['destination'])
    if not source.exists():
        raise FileNotFoundError(f'Source missing; never classify as an empty directory: {source}')
    guarded_destination(dest, canonical)
    if source.resolve() == dest.resolve() or source.resolve() in dest.resolve().parents:
        raise RuntimeError('Recursive/self copy forbidden')
    before = tree(source)
    print(f"COPY {entry['id']} files={len(before)} bytes={sum(v[1] for v in before.values())}", flush=True)
    if source.is_file():
        dest.parent.mkdir(parents=True, exist_ok=True)
        if not dest.exists():
            shutil.copy2(source, dest)
    else:
        dest.mkdir(parents=True, exist_ok=True)
        command = ['robocopy', str(source), str(dest), '/E', '/COPY:DAT', '/DCOPY:DAT',
                   '/XJ', '/R:1', '/W:1', '/XC', '/XN', '/XO', '/NFL', '/NDL', '/NP', '/MT:4']
        with (report / f"{entry['id']}_robocopy.txt").open('w', encoding='utf-8') as log:
            result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=7200)
        if result.returncode >= 8:
            raise RuntimeError(f"Robocopy failed: {entry['id']} code={result.returncode}")
    after = tree(dest)
    # A file mapping can deliberately give the preserved artifact a descriptive name.
    if source.is_file():
        after = {source.name: next(iter(after.values()))}
    if before.keys() != after.keys():
        raise RuntimeError(f"File-set mismatch: {entry['id']}")
    rows = []

    def compare(relative):
        src, size, modified = before[relative]
        dst, dst_size, dst_modified = after[relative]
        source_hash, dest_hash = sha256(src), sha256(dst)
        stable = src.stat().st_size == size and src.stat().st_mtime_ns == modified
        equal = stable and size == dst_size and source_hash == dest_hash
        row = dict(relative_path=relative, source_bytes=size, destination_bytes=dst_size,
                   source_sha256=source_hash, destination_sha256=dest_hash,
                   source_mtime_ns=modified, destination_mtime_ns=dst_modified,
                   source_stable=stable, timestamp_equal=modified == dst_modified,
                   result='PASS' if equal else 'FAIL')
        return row

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for index, row in enumerate(pool.map(compare, sorted(before)), 1):
            rows.append(row)
            if row['result'] != 'PASS':
                write_csv(report / f"{entry['id']}_FILES.csv", list(row), rows)
                raise RuntimeError(f"Hash/source stability mismatch: {entry['id']} {row['relative_path']}")
            if index % 10000 == 0:
                print(f"HASH {entry['id']} {index}/{len(before)}", flush=True)
    fields = list(rows[0]) if rows else ['relative_path', 'result']
    manifest = report / f"{entry['id']}_FILES.csv"
    write_csv(manifest, fields, rows)
    src_after = tree(source)
    if {k: (v[1], v[2]) for k, v in src_after.items()} != {k: (v[1], v[2]) for k, v in before.items()}:
        raise RuntimeError(f"Source tree changed during copy: {entry['id']}")
    return dict(id=entry['id'], source_path=str(source), destination_path=str(dest),
                classification=entry['classification'], file_count=len(rows),
                total_bytes=sum(row['source_bytes'] for row in rows),
                last_modified_utc=datetime.fromtimestamp(source.stat().st_mtime, timezone.utc).isoformat(),
                hash_verification='PASS', timestamp_verification='PASS' if all(row['timestamp_equal'] for row in rows) else 'REVIEW',
                manifest_sha256=sha256(manifest), bytes_reclaimed=0,
                source_untouched=True, retirement='PENDING_EXACT_USER_APPROVAL_AND_ALL_GATES')


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--canonical', type=Path, required=True)
    parser.add_argument('--reports', type=Path, required=True)
    parser.add_argument('--only', nargs='*')
    args = parser.parse_args()
    plan = json.loads(args.plan.read_text(encoding='utf-8-sig'))
    if len({item['id'] for item in plan}) != len(plan):
        raise RuntimeError('Duplicate job identifiers')
    args.reports.mkdir(parents=True, exist_ok=True)
    status_path = args.reports / 'copy_status.json'
    results = json.loads(status_path.read_text()) if status_path.exists() else {}
    for entry in plan:
        if args.only and entry['id'] not in args.only:
            continue
        try:
            result = copy_job(entry, args.reports, args.canonical)
            results[entry['id']] = result
            status_path.write_text(json.dumps(results, indent=2), encoding='utf-8')
            write_csv(args.reports / 'COPY_VERIFICATION.csv', list(result), results.values())
            write_csv(args.reports / 'STORAGE_INVENTORY.csv', list(result), results.values())
            write_csv(args.reports / 'PATH_MIGRATION_MAP.csv', ['id', 'source_path', 'destination_path', 'classification'],
                      ({k: item[k] for k in ('id', 'source_path', 'destination_path', 'classification')} for item in results.values()))
            print(f"PASS {entry['id']} files={result['file_count']} bytes={result['total_bytes']}", flush=True)
        except Exception as error:
            (args.reports / 'COPY_BLOCKER.json').write_text(json.dumps(dict(id=entry['id'], error=str(error), utc=datetime.now(timezone.utc).isoformat()), indent=2), encoding='utf-8')
            raise
    print(f'VERIFIED_JOBS={len(results)}/{len(plan)}', flush=True)


if __name__ == '__main__':
    main()
