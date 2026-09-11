plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.aibox.kotlin.core.model"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)

    // `@Immutable` 稳定性注解（仅注解，不引入 Compose 编译器插件）：
    // 让消息模型对 Compose 编译器“可跳过”，支撑 T04/D2「流式仅重组末尾项」。
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)

    testImplementation(libs.junit)
}
