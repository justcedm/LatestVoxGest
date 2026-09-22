package com.voxgest.dryrun.ui

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass> {
    error("LocalWindowSizeClass was not provided.")
}

@Composable
fun adaptiveSp(compact: Int, medium: Int, expanded: Int): TextUnit {
    val windowSizeClass = LocalWindowSizeClass.current
    return when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> compact.sp
        WindowWidthSizeClass.Medium -> medium.sp
        else -> expanded.sp
    }
}
