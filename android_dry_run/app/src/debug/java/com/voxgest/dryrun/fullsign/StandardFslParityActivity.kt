package com.voxgest.dryrun.fullsign

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import com.voxgest.dryrun.StandardFslParityHarness
import java.util.concurrent.Executors

/** ADB-launchable debug entry point; missing artifacts/fixtures show BLOCKED. */
class StandardFslParityActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var text: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            gravity = Gravity.START
            textSize = 13f
            typeface = Typeface.MONOSPACE
            text = "STANDARD_FSL_FULLSIGN225 parity: running"
        }
        setContentView(text)
        executor.execute {
            val report = StandardFslParityHarness(applicationContext).run()
            val output = buildString {
                appendLine("${report.feature.marker} ${report.feature.status}")
                appendLine(report.feature.evidence)
                appendLine()
                appendLine("${report.tflite.marker} ${report.tflite.status}")
                appendLine(report.tflite.evidence)
                appendLine()
                append("OVERALL ${if (report.passed) "PASS" else "NOT_PASS"}")
            }
            Log.i(TAG, "${report.feature.marker} ${report.feature.status} ${report.feature.evidence}")
            Log.i(TAG, "${report.tflite.marker} ${report.tflite.status} ${report.tflite.evidence}")
            runOnUiThread { text.text = output }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VoxGestFullSign225"
    }
}
