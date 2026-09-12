package com.voxgest.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.view.WindowCompat
import com.voxgest.dryrun.BuildConfig
import com.voxgest.dryrun.ui.VoxGestPresentationApp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.rgb(0, 108, 115)
        window.navigationBarColor = Color.rgb(0, 108, 115)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
            VoxGestPresentationApp(
                windowSizeClass = windowSizeClass,
                developerDiagnosticsEnabled =
                    BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEVELOPER_DIAGNOSTICS, false)
            )
        }
    }

    companion object {
        /** Debug-only, explicit entry point for retained legacy diagnostics. */
        const val EXTRA_DEVELOPER_DIAGNOSTICS =
            "com.voxgest.dryrun.extra.DEVELOPER_DIAGNOSTICS"
    }
}
