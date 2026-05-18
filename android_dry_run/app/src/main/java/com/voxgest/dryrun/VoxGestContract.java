package com.voxgest.dryrun;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VoxGestContract {
    public static final List<String> WORDS = Arrays.asList(
            "YES",
            "NO",
            "PLEASE",
            "WATER",
            "HELLO",
            "HELP",
            "STOP",
            "DOCTOR",
            "NAME",
            "THANKYOU"
    );

    public static final List<String> STABLE_DEMO_WORDS = Arrays.asList(
            "YES",
            "NO",
            "WATER",
            "HELLO",
            "THANKYOU"
    );

    public static final List<String> UNSTABLE_DEBUG_WORDS = Arrays.asList(
            "HELP",
            "STOP",
            "DOCTOR",
            "NAME",
            "PLEASE"
    );

    public static final List<String> DYNAMIC_LABELS = Arrays.asList(
            "YES",
            "NO",
            "PLEASE",
            "WATER",
            "HELLO",
            "HELP",
            "STOP",
            "DOCTOR",
            "NAME",
            "THANKYOU",
            "NOTHING"
    );

    public static final Set<String> WORD_SET = new HashSet<>(WORDS);

    private VoxGestContract() {
    }
}
