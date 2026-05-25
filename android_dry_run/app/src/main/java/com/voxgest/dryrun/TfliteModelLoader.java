package com.voxgest.dryrun;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public final class TfliteModelLoader {
    private final Context context;

    public TfliteModelLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    public Interpreter loadInterpreter(String... assetCandidates) throws IOException {
        return loadInterpreterWithOptions(null, assetCandidates);
    }

    public Interpreter loadInterpreterWithOptions(Interpreter.Options options, String... assetCandidates) throws IOException {
        IOException last = null;
        for (String assetPath : assetCandidates) {
            try {
                if (options == null) {
                    return new Interpreter(loadMappedAsset(assetPath));
                }
                return new Interpreter(loadMappedAsset(assetPath), options);
            } catch (IOException exc) {
                last = exc;
            }
        }
        throw last == null ? new IOException("No TFLite asset candidates supplied") : last;
    }

    public boolean assetExists(String assetPath) {
        try {
            context.getAssets().open(assetPath).close();
            return true;
        } catch (IOException exc) {
            return false;
        }
    }

    private MappedByteBuffer loadMappedAsset(String assetPath) throws IOException {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(assetPath);
             FileInputStream inputStream = new FileInputStream(descriptor.getFileDescriptor());
             FileChannel fileChannel = inputStream.getChannel()) {
            long startOffset = descriptor.getStartOffset();
            long declaredLength = descriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }
}
