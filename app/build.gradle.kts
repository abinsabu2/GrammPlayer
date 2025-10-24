import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aes.grammplayer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.aes.grammplayer"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use {
                localProperties.load(it)
            }
        }

        val apiId: Int = localProperties.getProperty("api_key")?.toIntOrNull() ?: error("API Key not found in local.properties")
        val apiHash: String = localProperties.getProperty("api_hash") ?: error("API Hash not found in local.properties")

        buildConfigField("int", "API_ID", apiId.toString())
        buildConfigField("String", "API_HASH", "\"$apiHash\"")

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 🔥 Dynamically rename release APK
    applicationVariants.all {
        outputs.all {
            val appName = "tgPlayer"
            val variant = this@all
            val versionCode = variant.versionCode
            val newApkName =
                "${appName}_v${versionName}_(${versionCode})_release.apk"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                newApkName
        }
    }
}

dependencies {
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.core.ktx)
    implementation(libs.glide)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
