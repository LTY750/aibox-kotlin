package com.aibox.kotlin.core.common

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, error: Throwable? = null)
    fun e(tag: String, message: String, error: Throwable? = null)
}

/** 默认实现只输出到 logcat；绝不记录 API Key、Token 或完整用户内容。 */
@Singleton
class AndroidLogger @Inject constructor() : Logger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun w(tag: String, message: String, error: Throwable?) {
        Log.w(tag, message, error)
    }

    override fun e(tag: String, message: String, error: Throwable?) {
        Log.e(tag, message, error)
    }
}
