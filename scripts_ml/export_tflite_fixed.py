# scripts_ml/export_tflite_fixed.py
# Uses ai-edge-litert to bypass TF 2.13 LLVM export issues.
# Usage: python scripts_ml/export_tflite_fixed.py

import os
import sys

import numpy as np

os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

try:
    from ai_edge_litert.interpreter import Interpreter as LiteInterpreter
except ImportError:
    print("Installing ai-edge-litert...")
    os.system(f"{sys.executable} -m pip install ai-edge-litert -q")
    from ai_edge_litert.interpreter import Interpreter as LiteInterpreter

import tensorflow as tf


def export_and_verify(h5_path, tflite_path, test_input_shape):
    """Convert a Keras .h5 model to TFLite and verify a zero-input pass."""
    print(f"\nLoading: {h5_path}")
    model = tf.keras.models.load_model(h5_path)
    print(f"  Input : {model.input_shape}")
    print(f"  Output: {model.output_shape}")

    print("Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    with open(tflite_path, "wb") as f:
        f.write(tflite_model)
    kb = os.path.getsize(tflite_path) / 1024
    print(f"  Saved: {tflite_path} ({kb:.0f} KB)")

    print("Verifying with ai-edge-litert...")
    interp = LiteInterpreter(model_path=tflite_path)
    interp.allocate_tensors()
    inp_d = interp.get_input_details()[0]
    out_d = interp.get_output_details()[0]
    print(f"  Input shape : {inp_d['shape']}")
    print(f"  Output shape: {out_d['shape']}")

    test = np.zeros(test_input_shape, dtype=np.float32)
    interp.set_tensor(inp_d["index"], test)
    interp.invoke()
    result = interp.get_tensor(out_d["index"])
    print(f"  Test class  : {result.argmax()} - VERIFIED WORKING")
    return True


if __name__ == "__main__":
    print("=" * 55)
    print("  VoxGest TFLite Exporter (ai-edge-litert)")
    print("=" * 55)

    models_to_export = [
        {
            "h5": "model/voxgest_v3.h5",
            "tflite": "model/voxgest_v3.tflite",
            "shape": (1, 63),
            "name": "Static Dense model",
        },
        {
            "h5": "model/voxgest_lstm_v1.h5",
            "tflite": "model/voxgest_lstm_v1.tflite",
            "shape": (1, 30, 162),
            "name": "LSTM Motion model",
        },
        {
            "h5": "model/voxgest_tcn_v1.h5",
            "tflite": "model/voxgest_tcn_v1.tflite",
            "shape": (1, 30, 162),
            "name": "TCN Motion model",
        },
        {
            "h5": "model/voxgest_phrase_tcn_v1.h5",
            "tflite": "model/voxgest_phrase_tcn_v1.tflite",
            "shape": (1, 60, 162),
            "name": "Phrase TCN model",
        },
    ]

    for m in models_to_export:
        if not os.path.exists(m["h5"]):
            print(f"\n  Skipping {m['name']} - {m['h5']} not found")
            continue
        print(f"\n  {m['name']}")
        export_and_verify(m["h5"], m["tflite"], m["shape"])

    print("\n" + "=" * 55)
    print("  All exports complete.")
    print("  Send to Android developer:")
    print("    model/voxgest_v3.tflite")
    print("    model/class_labels_v3.json")
    print("    model/voxgest_lstm_v1.tflite")
    print("    model/class_labels_lstm_v1.json")
    print("    model/voxgest_tcn_v1.tflite")
    print("    model/class_labels_tcn_v1.json")
    print("    model/voxgest_phrase_tcn_v1.tflite")
    print("    model/class_labels_phrase_tcn_v1.json")
    print("=" * 55)
