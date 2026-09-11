package com.aibox.kotlin.core.storage

import java.io.File

/**
 * 二进制 blob 存储（图片、附件内容、工具结果溢出等）。
 * storageKey 由实现生成（内容寻址），持久层只保存键。
 */
interface BlobStore {
    /** 保存字节并返回 storageKey。 */
    suspend fun save(bytes: ByteArray, ext: String? = null): String

    suspend fun read(storageKey: String): ByteArray?

    suspend fun readAsDataUrl(storageKey: String): String?

    suspend fun delete(storageKey: String)

    /**
     * 按 storageKey 取本地文件（Coil 直接加载 / 图片查看器使用）。
     *
     * 内容寻址实现下文件名为 `sha256.ext`，因此无需 IO 读取即可直接返回句柄；
     * 文件不存在时返回 `null`。仍需做路径安全校验（拒绝目录穿越）。
     */
    fun file(storageKey: String): File?

    /** blob 落盘目录（备份导入/导出使用）。 */
    val directory: File
}
