import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 内置 Kotlin 编译，不能再应用 org.jetbrains.kotlin.android。
    alias(libs.plugins.kotlin.compose)
}

val signingKeys = listOf(
    "KEYSTORE_FILE",
    "KEYSTORE_PASSWORD",
    "KEYSTORE_ALIAS",
    "KEYSTORE_ALIAS_PASSWORD",
)
val signingEnvironment = signingKeys.associateWith { providers.environmentVariable(it).orNull }
val hasSigningEnvironment = signingEnvironment.values.any { !it.isNullOrBlank() }
val signingPropertiesFile = rootProject.file("signing.properties")
val signingProperties = Properties().apply {
    if (!hasSigningEnvironment && signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}
val signingValues = if (hasSigningEnvironment || signingPropertiesFile.isFile) {
    signingKeys.associateWith { key ->
        val value = if (hasSigningEnvironment) {
            signingEnvironment[key]
        } else {
            signingProperties.getProperty(key)
        }
        require(!value.isNullOrBlank()) {
            "Release 签名配置缺少字段 $key，请检查 signing.properties 或对应环境变量"
        }
        value.trim()
    }
} else {
    emptyMap()
}

android {
    namespace = "moe.huiguang.pixiv"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "moe.huiguang.pixiv"
        minSdk = 34
        targetSdk = 37
        versionCode = 20
        versionName = "0.1.6"
    }

    val releaseSigning = if (signingValues.isNotEmpty()) {
        signingConfigs.create("release") {
            val rawStorePath = signingValues.getValue("KEYSTORE_FILE")
            val resolvedStoreFile = File(rawStorePath).let { candidate ->
                if (candidate.isAbsolute) candidate else rootProject.file(rawStorePath)
            }
            require(resolvedStoreFile.isFile) {
                "Release 签名密钥文件不存在: ${resolvedStoreFile.path}"
            }
            storeFile = resolvedStoreFile
            storePassword = signingValues.getValue("KEYSTORE_PASSWORD")
            keyAlias = signingValues.getValue("KEYSTORE_ALIAS")
            keyPassword = signingValues.getValue("KEYSTORE_ALIAS_PASSWORD")
        }
    } else {
        null
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_compiler_config.conf"))
}

dependencies {
    implementation(project(":data"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.runtime)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.coui)
    implementation(libs.coui.icons)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
}
