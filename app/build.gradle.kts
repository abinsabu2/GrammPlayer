import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val sanitizeForAmazonAppstore by tasks.registering(Exec::class) {
    group = "build"
    description = "Rename TDLib symbols that Amazon's scanner misclassifies as ad SDKs."
    commandLine("python3", rootProject.file("scripts/sanitize-for-amazon-appstore.py"))
}

val sanitizeReleaseDex by tasks.registering(Exec::class) {
    group = "build"
    description = "Remove androidx.media ADVERTISEMENT metadata constant from release DEX."
    dependsOn("minifyReleaseWithR8")
    val dexDir = layout.buildDirectory.dir("intermediates/dex/release/minifyReleaseWithR8")
    commandLine(
        "python3",
        rootProject.file("scripts/sanitize-for-amazon-appstore.py"),
        "--dex-dir",
        dexDir.get().asFile.absolutePath,
    )
}

tasks.named("preBuild") {
    dependsOn(sanitizeForAmazonAppstore)
}

afterEvaluate {
    tasks.findByName("packageRelease")?.dependsOn(sanitizeReleaseDex)
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
        versionCode = 2
        versionName = "1.1"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use {
                localProperties.load(it)
            }
        }

        val apiId: Int = localProperties.getProperty("api_key")?.toIntOrNull() ?: error("API Key not found in local.properties")
        val apiHash: String = localProperties.getProperty("api_hash") ?: error("API Hash not found in local.properties")

        // TMDB API Key (recommended)
        val tmdbKey: String = localProperties.getProperty("tmbd_key")
            ?: error("TMDB Key not found in local.properties")

        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbKey\"")
        buildConfigField("int", "API_ID", apiId.toString())
        buildConfigField("String", "API_HASH", "\"$apiHash\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
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
        buildConfig = true
        viewBinding = true
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

    // ✅ Fix for KAPT NonExistentClass errors (Retrofit/Room)
    kapt {
        correctErrorTypes = true
    }
}

dependencies {
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.core.ktx)
    implementation(libs.glide)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // ✅ Added OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // ✅ Retrofit + Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    implementation(libs.libvlc.all)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation("com.google.android.gms:play-services-basement:18.5.0")
}