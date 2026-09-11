plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.aibox.kotlin.feature.chat"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        // 供 debug 渲染样例页做 BuildConfig.DEBUG 守卫（样例 Activity 另置于 app-android debug 源集）。
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:conversation"))
    implementation(project(":core:provider"))
    implementation(project(":core:storage"))
    implementation(project(":feature:attachments"))
    implementation(project(":feature:model-selector"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    // T04 渲染能力（§6 依赖包清单）
    implementation(libs.compose.markdown.m3)      // GFM：表格/列表/引用/行内码/链接
    implementation(libs.highlights)               // 代码语法高亮
    implementation(libs.jlatexmath)               // LaTeX → Drawable
    implementation(libs.coil.compose)             // Blob 图片加载
    implementation(libs.telephoto.zoomable.coil)  // 双指缩放 / 双击查看器

    testImplementation(libs.junit)
}
