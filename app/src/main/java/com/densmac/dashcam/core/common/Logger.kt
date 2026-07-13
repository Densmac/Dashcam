package com.densmac.dashcam.core.common

import android.util.Log
import com.densmac.dashcam.BuildConfig

object Logger {
    private const val TAG = "Dashcam"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
