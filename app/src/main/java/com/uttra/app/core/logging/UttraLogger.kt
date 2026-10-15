package com.uttra.app.core.logging

import android.util.Log
import com.uttra.app.BuildConfig


@PublishedApi
internal val TAG = BuildConfig.LOG_TAG


inline fun logd(message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d(TAG, message())
    }
}

inline fun logi(message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.i(TAG, message())
    }
}

inline fun logw(message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.w(TAG, message())
    }
}

inline fun loge(
    throwable: Throwable? = null,
    message: () -> String
) {
    if (BuildConfig.DEBUG) {
        Log.e(TAG, message(), throwable)
    }
}
