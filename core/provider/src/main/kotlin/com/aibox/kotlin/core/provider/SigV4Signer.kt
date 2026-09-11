package com.aibox.kotlin.core.provider

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 签名器（纯函数，可单测）。
 * 旧版由 @ai-sdk/amazon-bedrock 库内部完成；Kotlin 侧按标准流程复刻：
 * canonical request → string-to-sign → HMAC-SHA256 派生链 → Authorization 头。
 */
object SigV4Signer {

    data class SignedRequest(
        val authorization: String,
        val amzDate: String,
        val securityTokenHeader: Map<String, String>,
    )

    /**
     * 计算签名并返回需要附加到请求的头。
     * @param method HTTP 方法（大写）
     * @param host 主机头值（不含端口，或 host:port 形式）
     * @param path 已编码的请求路径（与最终发出的完全一致）
     * @param query 规范化查询串（无 "?"，空串表示无查询）；多键按字典序
     * @param bodyBytes 请求体
     */
    fun sign(
        method: String,
        host: String,
        path: String,
        query: String,
        bodyBytes: ByteArray,
        accessKeyId: String,
        secretAccessKey: String,
        sessionToken: String?,
        region: String,
        service: String,
        amzDate: String,
        additionalHeaders: Map<String, String> = emptyMap(),
    ): SignedRequest {
        val payloadHash = sha256Hex(bodyBytes)

        // 参与签名的头：host + x-amz-* + 调用方附加头（如 content-type），按名称字典序
        val headers = sortedMapOf<String, String>()
        headers["host"] = host
        headers["x-amz-date"] = amzDate
        sessionToken?.let { headers["x-amz-security-token"] = it }
        headers.putAll(additionalHeaders)

        val canonicalHeaders = headers.entries.joinToString("") { "${it.key}:${it.value.trim()}\n" }
        val signedHeaders = headers.keys.joinToString(";")

        val canonicalRequest = buildString {
            append(method.uppercase()).append('\n')
            append(path).append('\n')
            append(query).append('\n')
            append(canonicalHeaders).append('\n')
            append(signedHeaders).append('\n')
            append(payloadHash)
        }

        val dateStamp = amzDate.substring(0, 8)
        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = buildString {
            append("AWS4-HMAC-SHA256\n")
            append(amzDate).append('\n')
            append(credentialScope).append('\n')
            append(sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8)))
        }

        val kDate = hmacSha256("AWS4$secretAccessKey".toByteArray(StandardCharsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        val kSigning = hmacSha256(kService, "aws4_request")
        val signature = hex(hmacSha256(kSigning, stringToSign))

        val tokenHeader = sessionToken?.let { mapOf("x-amz-security-token" to it) } ?: emptyMap()
        return SignedRequest(
            authorization = "AWS4-HMAC-SHA256 Credential=$accessKeyId/$credentialScope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature",
            amzDate = amzDate,
            securityTokenHeader = tokenHeader,
        )
    }

    fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hex(bytes: ByteArray): String = buildString {
        for (b in bytes) {
            append("0123456789abcdef"[(b.toInt() shr 4) and 0xf])
            append("0123456789abcdef"[b.toInt() and 0xf])
        }
    }
}
