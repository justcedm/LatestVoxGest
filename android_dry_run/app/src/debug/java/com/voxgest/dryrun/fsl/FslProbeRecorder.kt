package com.voxgest.dryrun.fsl

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Appends complete, non-overlapping probe attempts to app-internal CSV and JSONL journals.
 *
 * Attempts are capped per normalized expected label. A rejected cap/overlap request performs no
 * journal or feature-window write. Existing JSONL entries are inspected lazily, so the cap and
 * non-overlap invariant survive recreation of this recorder in the same app installation.
 */
class FslProbeRecorder(
    context: Context,
    val maxAttemptsPerExpected: Int = DEFAULT_MAX_ATTEMPTS_PER_EXPECTED,
    private val exportPerWindowJson: Boolean = true,
    rootDirectoryName: String = DEFAULT_ROOT_DIRECTORY
) {
    private val exporter = FslFeatureExporter(context, rootDirectoryName)
    private val stateBySessionPath = mutableMapOf<String, SessionState>()

    init {
        require(maxAttemptsPerExpected > 0) { "Attempt cap must be positive" }
    }

    @Synchronized
    fun record(attempt: FslProbeAttempt): FslProbeRecordResult {
        val paths = exporter.pathsForSession(attempt.metadata.sessionId)
        val state = stateBySessionPath.getOrPut(paths.sessionDirectoryPath) {
            restoreState(paths)
        }
        val expectedKey = normalizedExpectedKey(attempt.metadata.expected)
        val priorExpectedCount = state.expectedCounts[expectedKey] ?: 0

        if (priorExpectedCount >= maxAttemptsPerExpected) {
            return rejectedResult(
                reason = REJECTION_ATTEMPT_CAP,
                paths = paths,
                priorExpectedCount = priorExpectedCount,
                totalSessionAttempts = state.totalAttempts
            )
        }

        val previousWindowEnd = state.lastWindowEndMs
        if (previousWindowEnd != null && attempt.window.startedAtMs <= previousWindowEnd) {
            return rejectedResult(
                reason = REJECTION_OVERLAPPING_WINDOW,
                paths = paths,
                priorExpectedCount = priorExpectedCount,
                totalSessionAttempts = state.totalAttempts
            )
        }

        exporter.ensureSessionDirectory(paths)
        val nextExpectedCount = priorExpectedCount + 1
        val attemptId = buildAttemptId(attempt, nextExpectedCount)
        val featureExport = if (exportPerWindowJson) {
            exporter.exportWindow(attempt, nextExpectedCount, attemptId)
        } else {
            null
        }
        val summary = attempt.toSummaryJson(
            attemptId = attemptId,
            attemptForExpected = nextExpectedCount,
            expectedKey = expectedKey,
            windowRelativePath = featureExport?.relativePath
        )

        appendJsonLine(File(paths.jsonlPath), summary.toString())
        appendCsvRow(File(paths.csvPath), summary)

        state.expectedCounts[expectedKey] = nextExpectedCount
        state.totalAttempts += 1
        state.lastWindowEndMs = attempt.window.endedAtMs

        return FslProbeRecordResult(
            recorded = true,
            rejectionReason = null,
            attemptForExpected = nextExpectedCount,
            totalSessionAttempts = state.totalAttempts,
            maxAttemptsPerExpected = maxAttemptsPerExpected,
            paths = paths,
            windowJsonPath = featureExport?.absolutePath
        )
    }

    @Synchronized
    fun recordedCount(sessionId: String, expected: String): Int {
        requireSafeProbeText("session id", sessionId)
        requireSafeProbeText("expected label", expected)
        val paths = exporter.pathsForSession(sessionId)
        val state = stateBySessionPath.getOrPut(paths.sessionDirectoryPath) {
            restoreState(paths)
        }
        return state.expectedCounts[normalizedExpectedKey(expected)] ?: 0
    }

    private fun rejectedResult(
        reason: String,
        paths: FslProbePaths,
        priorExpectedCount: Int,
        totalSessionAttempts: Int
    ): FslProbeRecordResult {
        return FslProbeRecordResult(
            recorded = false,
            rejectionReason = reason,
            attemptForExpected = priorExpectedCount,
            totalSessionAttempts = totalSessionAttempts,
            maxAttemptsPerExpected = maxAttemptsPerExpected,
            paths = paths,
            windowJsonPath = null
        )
    }

    private fun restoreState(paths: FslProbePaths): SessionState {
        val state = SessionState()
        val jsonlFile = File(paths.jsonlPath)
        if (!jsonlFile.isFile) return state

        jsonlFile.useLines { lines ->
            lines.filter(String::isNotBlank).forEachIndexed { index, line ->
                val item = try {
                    JSONObject(line)
                } catch (error: Exception) {
                    throw IllegalStateException(
                        "Invalid FSL probe JSONL at ${jsonlFile.absolutePath}:${index + 1}",
                        error
                    )
                }
                val expectedKey = item.getString("expected_key")
                state.expectedCounts[expectedKey] = (state.expectedCounts[expectedKey] ?: 0) + 1
                state.totalAttempts += 1
                val endedAtMs = item.getLong("window_ended_at_ms")
                state.lastWindowEndMs = maxOf(state.lastWindowEndMs ?: Long.MIN_VALUE, endedAtMs)
            }
        }
        return state
    }

    private fun FslProbeAttempt.toSummaryJson(
        attemptId: String,
        attemptForExpected: Int,
        expectedKey: String,
        windowRelativePath: String?
    ): JSONObject {
        return FslFeatureExporter.metadataToJson(metadata)
            .put("schema_version", SUMMARY_SCHEMA_VERSION)
            .put("attempt_id", attemptId)
            .put("attempt_for_expected", attemptForExpected)
            .put("expected_key", expectedKey)
            .put("window_started_at_ms", window.startedAtMs)
            .put("window_ended_at_ms", window.endedAtMs)
            .put("pose_frame_count", window.poseFrameCount)
            .put("right_hand_frame_count", window.rightHandFrameCount)
            .put("window_json_path", windowRelativePath ?: JSONObject.NULL)
    }

    private fun appendJsonLine(file: File, line: String) {
        FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.append(line)
            writer.newLine()
        }
    }

    private fun appendCsvRow(file: File, summary: JSONObject) {
        val needsHeader = !file.exists() || file.length() == 0L
        FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use { writer ->
            if (needsHeader) {
                writer.append(CSV_COLUMNS.joinToString(","))
                writer.newLine()
            }
            writer.append(CSV_COLUMNS.joinToString(",") { column -> csvValue(summary, column) })
            writer.newLine()
        }
    }

    private fun csvValue(summary: JSONObject, column: String): String {
        val raw = when (column) {
            "shape" -> summary.getJSONArray(column).toString()
            "top1_label" -> summary.getJSONObject("top1").getString("label")
            "top1_score" -> summary.getJSONObject("top1").getDouble("score").toString()
            "top2_label" -> summary.getJSONObject("top2").getString("label")
            "top2_score" -> summary.getJSONObject("top2").getDouble("score").toString()
            else -> if (summary.isNull(column)) "" else summary.get(column).toString()
        }
        return csvEscape(raw)
    }

    private fun csvEscape(value: String): String {
        val normalized = value.replace("\r", " ").replace("\n", " ")
        return if (normalized.any { it == ',' || it == '"' }) {
            "\"${normalized.replace("\"", "\"\"")}\""
        } else {
            normalized
        }
    }

    private fun buildAttemptId(attempt: FslProbeAttempt, attemptForExpected: Int): String {
        val session = FslFeatureExporter.safeFileToken(attempt.metadata.sessionId, "session")
        val expected = FslFeatureExporter.safeFileToken(attempt.metadata.expected, "expected")
        return String.format(
            Locale.US,
            "%s_%s_%03d_%d",
            session,
            expected,
            attemptForExpected,
            attempt.metadata.timestampMs
        )
    }

    private fun normalizedExpectedKey(expected: String): String {
        return expected
            .trim()
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
            .ifBlank { "EXPECTED" }
    }

    private data class SessionState(
        val expectedCounts: MutableMap<String, Int> = mutableMapOf(),
        var totalAttempts: Int = 0,
        var lastWindowEndMs: Long? = null
    )

    companion object {
        const val DEFAULT_MAX_ATTEMPTS_PER_EXPECTED = 5
        const val REJECTION_ATTEMPT_CAP = "attempt_cap_reached"
        const val REJECTION_OVERLAPPING_WINDOW = "overlapping_or_out_of_order_window"

        private const val DEFAULT_ROOT_DIRECTORY = "fsl_probe"
        private const val SUMMARY_SCHEMA_VERSION = "voxgest_fsl_probe_summary_v1"
        private val CSV_COLUMNS = listOf(
            "schema_version",
            "attempt_id",
            "attempt_for_expected",
            "feature_version",
            "shape",
            "dtype",
            "expected",
            "predicted",
            "top1_label",
            "top1_score",
            "top2_label",
            "top2_score",
            "margin",
            "latency_ms",
            "accepted",
            "state",
            "timestamp_ms",
            "timestamp_iso_utc",
            "signer_id",
            "session_id",
            "device_id",
            "model_id",
            "profile_id",
            "expected_key",
            "window_started_at_ms",
            "window_ended_at_ms",
            "pose_frame_count",
            "right_hand_frame_count",
            "window_json_path"
        )
    }
}
