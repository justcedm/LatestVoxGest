"""Save bounded ADB command evidence locally, never commit device logs/screenshots."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "reports/device_tests/samsung"


def main() -> int:
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--session", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--serial")
    parser.add_argument("--binary", action="store_true")
    parser.add_argument("--timeout", type=int, default=25)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    for value in (args.session, args.name):
        if not value or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-" for c in value):
            parser.error("Session/name must be safe filename tokens")
    directory = EVIDENCE / args.session
    directory.mkdir(parents=True, exist_ok=True)
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    argv = [str(args.adb)] + (["-s", args.serial] if args.serial else []) + command
    started = time.monotonic()
    record = {"utc": datetime.now(timezone.utc).isoformat(), "serial": args.serial, "command": command}
    try:
        result = subprocess.run(argv, capture_output=True, timeout=args.timeout)
        stdout, stderr, code = result.stdout, result.stderr, result.returncode
    except subprocess.TimeoutExpired as error:
        stdout, stderr, code = error.stdout or b"", error.stderr or b"", 124
        record["timeout"] = True
    record.update(returncode=code, elapsed_seconds=time.monotonic() - started)
    extension = "png" if args.binary and command[-1:] == ["-p"] else "bin" if args.binary else "txt"
    output = directory / f"{args.name}.{extension}"
    if output.exists():
        parser.error("Evidence name already exists; choose a new name (never overwrite)")
    output.write_bytes(stdout)
    (directory / f"{args.name}.stderr.txt").write_bytes(stderr)
    (directory / f"{args.name}.meta.json").write_text(json.dumps(record, indent=2), encoding="utf-8")
    print(f"EVIDENCE={output.relative_to(ROOT).as_posix()} RETURN_CODE={code}")
    if not args.binary:
        print(stdout.decode("utf-8", errors="replace")[-18000:])
    if stderr:
        print(stderr.decode("utf-8", errors="replace")[-2000:])
    return code


if __name__ == "__main__":
    raise SystemExit(main())
