package com.aibox.kotlin.core.common

import java.util.UUID

/** 生成与旧版一致的 uuid v4 字符串。 */
fun newId(): String = UUID.randomUUID().toString()
