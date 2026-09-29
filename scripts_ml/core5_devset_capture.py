"""Future interactive CORE5_DEVSET_V1 capture from recognition-lab ONLY.

This script is deliberately not invoked during offline preparation. A second
observer should press the marker keys while the signer performs one event.
No Android production package is addressed and no model is installed here.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import msvcrt
import re
import shlex
import subprocess
import time
from pathlib import Path

from core5_devset_v1 import (LAB_PACKAGE, MODEL_HASHES, NEGATIVE_TO_APP,
                             bind_device, digest, ingest, json_bytes, load,
                             seal, write_new)

ACTIVITY = "com.voxgest.dryrun.Core5DiagnosticActivity"
EVENT_NAME = re.compile(r"[0-9a-f]{8}-[0-9a-f-]{27,}\.json\Z")
MARKERS = ("SIGN_START", "SIGN_END", "RETURN_NEUTRAL", "SAFE_REARM")


def adb(adb_path: Path, serial: str | None, *args: str, binary: bool = False):
    command = [str(adb_path)]
    if serial:
        command += ["-s", serial]
    command += list(args)
    result = subprocess.run(command, capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError("ADB command failed: " + " ".join(command[:5]) + ": " +
                           result.stderr.decode("utf-8", "replace")[:300])
    return result.stdout if binary else result.stdout.decode("utf-8", "replace").strip()


def single_authorized_serial(adb_path: Path) -> str:
    output = adb(adb_path, None, "devices", "-l")
    records = [row.split() for row in output.splitlines()[1:] if row.strip()]
    unauthorized = [r[0] for r in records if len(r) > 1 and r[1] != "device"]
    available = [r[0] for r in records if len(r) > 1 and r[1] == "device"]
    if len(available) != 1 or unauthorized:
        raise RuntimeError("Require exactly one authorized ADB device; devices -l: " + output)
    return available[0]


def device_ms(adb_path: Path, serial: str):
    """Map host monotonic time to Android elapsedRealtime via /proc/uptime."""
    before = time.perf_counter_ns()
    output = adb(adb_path, serial, "shell", "cat /proc/uptime")
    after = time.perf_counter_ns()
    uptime_ms = float(output.split()[0]) * 1000
    midpoint = (before + after) / 2 / 1e6
    return midpoint, uptime_ms, (after - before) / 2e6 + 10  # 10 ms /proc rounding allowance


def event_names(adb_path: Path, serial: str):
    output = adb(adb_path, serial, "shell", "run-as", LAB_PACKAGE,
                 "ls", "files/core5_diagnostics")
    return {name for name in output.splitlines() if EVENT_NAME.fullmatch(name)}


def pull_event(adb_path: Path, serial: str, filename: str) -> bytes:
    if not EVENT_NAME.fullmatch(filename):
        raise ValueError("Unsafe event filename")
    return adb(adb_path, serial, "exec-out", "run-as", LAB_PACKAGE,
               "cat", "files/core5_diagnostics/" + filename, binary=True)


def marker_session(host_to_device_offset: float, hold: bool = False):
    print("Observer: press SPACE once at each cue below. Signer never repeats inside one event.")
    print("For NEUTRAL, SIGN_START/END bracket stillness; they are not linguistic signs.")
    result = {}
    sequence = ("SIGN_START", "HOLD_START", "HOLD_END", "SIGN_END",
                "RETURN_NEUTRAL", "SAFE_REARM") if hold else MARKERS
    for name in sequence:
        print(name + " [SPACE]: ", end="", flush=True)
        while msvcrt.getwch() != " ":
            pass
        host_ms = time.perf_counter_ns() / 1e6
        result[name] = round(host_ms + host_to_device_offset)
        print(result[name])
    return result


def shell_am(adb_path: Path, serial: str, *options: str):
    command = "am start -n " + LAB_PACKAGE + "/" + ACTIVITY
    command += " " + " ".join(shlex.quote(item) for item in options)
    return adb(adb_path, serial, "shell", command)


def capture(args):
    root = args.root.resolve()
    manifest = load(root)
    seal(root)
    if args.trial_id not in manifest["slots"] or manifest["slots"][args.trial_id]["status"] != "PLANNED":
        raise ValueError("Capture requires one unused, preplanned trial ID")
    slot = manifest["slots"][args.trial_id]
    serial = single_authorized_serial(args.adb)
    if args.serial and serial != args.serial:
        raise ValueError("Connected device serial differs from requested serial")
    packages = adb(args.adb, serial, "shell", "pm list packages " + LAB_PACKAGE)
    if "package:" + LAB_PACKAGE not in packages.splitlines():
        raise RuntimeError("Recognition-lab package is not installed; modern app is untouched")
    # Restart ONLY the separate lab package so an old baseline activity cannot
    # silently ignore the candidate variant extra in its singleTop onNewIntent.
    existing_startups = set(adb(args.adb, serial, "shell", "run-as", LAB_PACKAGE,
                                "ls", "files/core5_diagnostics").splitlines())
    adb(args.adb, serial, "shell", "am force-stop " + LAB_PACKAGE)
    shell_am(args.adb, serial, "--es", "core5_variant", "sparse10fps_candidate")
    time.sleep(3)
    startup_files = adb(args.adb, serial, "shell", "run-as", LAB_PACKAGE,
                        "ls", "files/core5_diagnostics").splitlines()
    startups = sorted(name for name in startup_files if name.startswith("startup-") and name.endswith(".json")
                      and name not in existing_startups)
    if len(startups) != 1:
        raise RuntimeError("Expected exactly one fresh recognition-lab startup parity record")
    startup = json.loads(adb(args.adb, serial, "exec-out", "run-as", LAB_PACKAGE,
                             "cat", "files/core5_diagnostics/" + startups[-1], binary=True))
    if startup.get("profile") != "FSL_CORE5_SIM10FPS_V1" or startup.get("model_sha256") != MODEL_HASHES["sim10_output"]:
        raise RuntimeError("Recognition-lab candidate startup/model parity failed")
    before_events = event_names(args.adb, serial)
    sync_start = device_ms(args.adb, serial)
    if sync_start[2] > 100:
        raise RuntimeError("ADB clock synchronization too uncertain")
    if args.preflight_only:
        marker_session(sync_start[1] - sync_start[0], args.hold)
        sync_end = device_ms(args.adb, serial)
        uncertainty = max(sync_start[2], sync_end[2],
                          abs((sync_end[1] - sync_end[0]) - (sync_start[1] - sync_start[0])) / 2)
        print(json.dumps({"package": LAB_PACKAGE, "profile": startup["profile"],
                          "model_sha256": startup["model_sha256"],
                          "marker_clock_uncertainty_ms": uncertainty,
                          "dry_run_only": True, "event_started": False}, indent=2))
        if uncertainty > 100:
            raise RuntimeError("Marker clock dry-run uncertainty exceeds 100 ms")
        return
    bind_device(root, slot["lane"], serial)

    expected = NEGATIVE_TO_APP.get(slot["target"], slot["target"])
    input("Signer framed and neutral, observer at keyboard? Press ENTER to BEGIN: ")
    shell_am(args.adb, serial, "--es", "command", "begin", "--es", "expected", expected,
             "--es", "mode", "MANUAL")
    try:
        markers = marker_session(sync_start[1] - sync_start[0], args.hold)
    finally:
        # A missing marker is still an event; preserve it on-device, do not
        # silently replace it with another sign in the same trial slot.
        time.sleep(.2)  # allow a post-SAFE_REARM analyzer observation before END
        shell_am(args.adb, serial, "--es", "command", "end")
    time.sleep(.5)
    sync_end = device_ms(args.adb, serial)
    uncertainty = max(sync_start[2], sync_end[2],
                      abs((sync_end[1] - sync_end[0]) - (sync_start[1] - sync_start[0])) / 2)
    new_events = event_names(args.adb, serial) - before_events
    if len(new_events) != 1:
        raise RuntimeError(f"Expected exactly one new lab event; found {len(new_events)}. "
                           "Preserve device evidence and reconcile before another trial.")
    event_bytes = pull_event(args.adb, serial, next(iter(new_events)))
    event = json.loads(event_bytes)
    pending = root / "pending" / args.trial_id
    if pending.exists():
        raise FileExistsError("Pending evidence already exists; reconcile, never overwrite")
    pending.mkdir(parents=True)
    write_new(pending / "raw_capture.json", event_bytes)
    annotations = {"trial_id": args.trial_id, "source_event_id": event["event_id"],
                   "capture_package": LAB_PACKAGE,
                   "capture_timestamp": dt.datetime.now(dt.timezone.utc).isoformat(),
                   "clock_uncertainty_ms": uncertainty, **markers}
    write_new(pending / "annotations.json", json_bytes(annotations))
    write_new(pending / "adb_source.json", json_bytes({"package": LAB_PACKAGE,
              "device_serial_sha256": digest(serial.encode()), "filename": next(iter(new_events)),
              "raw_capture_sha256": digest(event_bytes), "startup_profile": startup["profile"],
              "clock_source": "Android /proc/uptime versus host monotonic; physical timing unverified"}))
    if uncertainty > 100:
        raise RuntimeError(f"Clock uncertainty {uncertainty:.1f} ms; pending raw evidence retained")
    result = ingest(root, args.trial_id, pending / "raw_capture.json",
                    pending / "annotations.json", args.baseline, args.native_zip,
                    args.sim, args.notes)
    print(json.dumps(result, indent=2))
    print("Pending source evidence retained at", pending)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--trial-id", required=True)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial")
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--native-zip", type=Path, required=True)
    parser.add_argument("--sim", type=Path, required=True)
    parser.add_argument("--notes", default="")
    parser.add_argument("--hold", action="store_true", help="also mark HOLD_START/HOLD_END")
    parser.add_argument("--preflight-only", action="store_true",
                        help="verify lab startup and marker clock without beginning an event")
    capture(parser.parse_args())


if __name__ == "__main__":
    main()
