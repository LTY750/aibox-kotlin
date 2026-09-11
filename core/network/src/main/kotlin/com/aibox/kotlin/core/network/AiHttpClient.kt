package com.aibox.kotlin.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI HTTP 客户端封装：
 * - OkHttp 引擎，长超时（SSE 流式会长时间保持连接）
 * - 非 2xx 响应统一映射为 ApiError，HTML 错误页会被替换为简短文本
 * - 取消通过协程取消传播（AbortError 语义），绝不重试
 */
@Singleton
class AiHttpClient @Inject constructor() : AutoCloseable {

    val client: HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(10, TimeUnit.MINUTES)
                writeTimeout(60, TimeUnit.SECONDS)
                retryOnConnectionFailure(false)
            }
        }
        expectSuccess = false
    }

    /** 执行请求并对非 2xx 响应抛出 ApiError。 */
    suspend fun execute(block: HttpRequestBuilder.() -> Unit): HttpResponse {
        val response = client.request { block() }
        if (!response.status.isSuccess()) {
            val body = try {
                response.bodyAsText()
            } catch (_: Exception) {
                ""
            }
            throw mapResponseError(response.status.value, body)
        }
        return response
    }

    companion object {
        fun mapResponseError(statusCode: Int, body: String): ApiError =
            ApiError(
                statusCode = statusCode,
                message = "API Error: ${sanitizeErrorBody(statusCode, body).take(500)}",
                responseBody = sanitizeErrorBody(statusCode, body),
            )

        /** 将 Ktor/OkHttp 异常映射为领域错误（非 suspend，不读取响应体）。 */
        fun mapThrowable(t: Throwable): Throwable = when (t) {
            is ApiError, is NetworkError, is MidStreamApiError -> t
            is ClientRequestException -> mapResponseError(t.response.status.value, "")
            is ServerResponseException -> mapResponseError(t.response.status.value, "")
            else -> NetworkError(t.message ?: "network error", t)
        }
    }

    override fun close() {
        client.close()
    }
}

/** 便捷的 JSON POST 构造。 */
fun HttpRequestBuilder.jsonPost(urlString: String, body: String, headers: Map<String, String> = emptyMap()) {
    method = HttpMethod.Post
    url { takeFrom(urlString) }
    contentType(ContentType.Application.Json)
    headers.forEach { (k, v) -> header(k, v) }
    setBody(body)
}
