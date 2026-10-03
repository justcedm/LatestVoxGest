"""Read-only APK/native alignment and frozen Practical15 golden contract verification."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "android_dry_run/app/src/main/assets"
PROFILE = "model/fsl_practical15_fullsign225_48f_v1"


def elf_load_alignments(data: bytes) -> list[int]:
    if data[:4] != b"\x7fELF" or data[5] != 1:
        raise ValueError("Unsupported ELF header")
    if data[4] == 2:
        offset = struct.unpack_from("<Q", data, 32)[0]
        size, count = struct.unpack_from("<HH", data, 54)
        return [struct.unpack_from("<Q", data, offset + i * size + 48)[0]
                for i in range(count) if struct.unpack_from("<I", data, offset + i * size)[0] == 1]
    if data[4] == 1:
        offset = struct.unpack_from("<I", data, 28)[0]
        size, count = struct.unpack_from("<HH", data, 42)
        return [struct.unpack_from("<I", data, offset + i * size + 28)[0]
                for i in range(count) if struct.unpack_from("<I", data, offset + i * size)[0] == 1]
    raise ValueError("Unsupported ELF class")


def main() -> int:
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("--apk", type=Path, default=ROOT / "android_dry_run/app/build/outputs/apk/debug/app-debug.apk")
    args = parser.parse_args()
    import tensorflow as tf

    manifest = json.loads((ASSETS / PROFILE / "runtime_manifest.json").read_text())
    hashes = {}
    for field in ("model", "labels"):
        content = (ASSETS / PROFILE / manifest[f"{field}_file"]).read_bytes()
        hashes[field] = hashlib.sha256(content).hexdigest()
        assert hashes[field] == manifest[f"{field}_sha256"]
    interpreter = tf.lite.Interpreter(model_path=str(ASSETS / PROFILE / manifest["model_file"]))
    interpreter.allocate_tensors()
    inp, out = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    assert inp["shape"].tolist() == [1, 48, 225] and out["shape"].tolist() == [1, 15]
    assert inp["dtype"] == np.float32 and out["dtype"] == np.float32
    window = np.fromfile(ASSETS / PROFILE / "golden_fullsign225_window_f32.bin", dtype="<f4").reshape(1, 48, 225)
    expected = json.loads((ASSETS / PROFILE / "golden_fullsign225_window_expected.json").read_text())
    interpreter.set_tensor(inp["index"], window)
    interpreter.invoke()
    probabilities = interpreter.get_tensor(out["index"])[0]
    difference = float(np.max(np.abs(probabilities - np.asarray(expected["expected_probabilities"], dtype=np.float32))))
    assert difference <= 1e-5 and int(np.argmax(probabilities)) == expected["expected_index"]
    natives = []
    with zipfile.ZipFile(args.apk) as apk, args.apk.open("rb") as file:
        for field in ("model", "labels"):
            packaged = apk.read(f"assets/{PROFILE}/{manifest[f'{field}_file']}")
            assert hashlib.sha256(packaged).hexdigest() == hashes[field]
        for item in apk.infolist():
            if not item.filename.startswith("lib/") or not item.filename.endswith(".so"):
                continue
            alignments = elf_load_alignments(apk.read(item))
            file.seek(item.header_offset)
            header = file.read(30)
            name_size, extra_size = struct.unpack_from("<HH", header, 26)
            data_offset = item.header_offset + 30 + name_size + extra_size
            natives.append({"library": item.filename, "min_load_alignment": min(alignments),
                            "elf_load_16k": all(value >= 16384 for value in alignments),
                            "zip_16k": item.compress_type != zipfile.ZIP_STORED or data_offset % 16384 == 0})
    result = {"profile": manifest["profile_id"], "hashes": hashes, "golden_max_difference": difference,
              "golden_top1": expected["expected_label"], "apk_asset_parity": "PASS",
              "abis": sorted({item["library"].split('/')[1] for item in natives}),
              "native_libraries": len(natives), "elf_load_failures": [n for n in natives if not n["elf_load_16k"]],
              "zip_alignment_failures": [n for n in natives if not n["zip_16k"]],
              "physical_16k_compatibility": "NOT_TESTED; static PT_LOAD/ZIP checks only; RELRO/runtime not certified"}
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
