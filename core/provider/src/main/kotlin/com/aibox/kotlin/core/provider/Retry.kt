package com.aibox.kotlin.core.provider

import com.aibox.kotlin.core.network.ApiError
import com.aibox.kotlin.core.network.MidStreamApiError
import com.aibox.kotlin.core.network.NetworkError
import com.aibox.kotlin.core.network.isRetryableStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 流级重试：内容未流出前，429/5xx 与网络错误按指数退避自动重试。
 * 已流出内容后绝不重试（计费安全）；MidStreamApiError 与取消异常直接透传。
 */
fun Flow<StreamEvent>.withAiRetry(
    maxAttempts: Int = RetryPolicy.MAX_ATTEMPTS,
    initialDelayMs: Long = RetryPolicy.INITIAL_DELAY_MS,
    factor: Double = RetryPolicy.BACKOFF_FACTOR,
): Flow<StreamEvent> = flow {
    var attempt = 0

    while (true) {
        var contentForwarded = false
        var terminalError: Throwable? = null
        try {
            collect { event ->
                when (event) {
                    is StreamEvent.TextDelta,
                    is StreamEvent.ReasoningDelta,
                    is StreamEvent.ToolCallStart,
                    is StreamEvent.ToolCallEnd,
                    is StreamEvent.Image,
                    -> contentForwarded = true
                    else -> Unit
                }
                if (event is StreamEvent.Error) {
                    terminalError = event.error
                } else {
                    emit(event)
                }
            }
            if (terminalError == null) return@flow // 正常完成
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            terminalError = e
        }

        val error = terminalError ?: return@flow
        val retryable = !contentForwarded &&
            error !is MidStreamApiError &&
            (
                (error is ApiError && isRetryableStatus(error.statusCode)) ||
                    error is NetworkError
                )
        if (!retryable || attempt >= maxAttempts - 1) {
            emit(StreamEvent.Error(error))
            return@flow
        }
        attempt++
        emit(StreamEvent.Retrying(attempt + 1, maxAttempts, error))
        delay((initialDelayMs * Math.pow(factor, (attempt - 1).toDouble())).toLong())
    }
}
