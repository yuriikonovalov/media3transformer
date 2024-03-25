package com.yuriikonovalov.media3

import android.util.Log

private const val TAG = "FRWC190324"
fun Any.log(message: String) {
    Log.d(TAG + " (${this::class.simpleName})", message)
}