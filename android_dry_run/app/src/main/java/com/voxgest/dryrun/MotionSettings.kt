package com.voxgest.dryrun

import android.content.Context
import android.provider.Settings

object MotionSettings {
    fun animationsEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            ) != 0f
        } catch (_: SecurityException) {
            true
        } catch (_: Settings.SettingNotFoundException) {
            true
        }
    }
}
