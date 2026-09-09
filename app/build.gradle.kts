plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("com.google.devtools.ksp")
}

android {

    namespace = "com.tans.tfiletransporter"
    compileSdk = properties["ANDROID_COMPILE_SDK"].toString().toInt()

    defaultConfig {
        applicationId = "com.tans.tfiletransporter"
        minSdk = properties["ANDROID_MIN_SDK"].toString().toInt()
        targetSdk = properties["ANDROID_TARGET_SDK"].toString().toInt()
        versionCode = properties["VERSION_CODE"].toString().toInt()
        versionName = properties["VERSION_NAME"].toString()
    }

    packaging {
        resources {
            excludes.addAll(listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties"))
        }
    }


    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("releaseConfig") {
            storeFile = file("debugkey/debug.jks")
            storePassword = "123456"
            keyAlias = "key0"
            keyPassword = "123456"
        }
    }

    buildTypes {
        debug {
            multiDexEnabled = true
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            multiDexEnabled = true
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("releaseConfig")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        viewBinding {
            enable = true
        }
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)

    // coroutine
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.core.jvm)
    implementation(libs.coroutines.android)

    // moshi
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    ksp(libs.moshi.kotlin.codegen)

    // lifecycle
    implementation(libs.androidx.lifecycle.rumtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    ksp(libs.androidx.lifecycle.compiler)

    // camerax
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.view)
    implementation(libs.camerax.extensions)

    // barcode scan
    implementation(libs.barcodescan)

    // qrcode gen
    implementation(libs.qrcodegen)

    // tans5
    implementation(libs.tuiutils)

    // glide
    implementation(libs.glide)

    // tapm
    implementation(libs.tapm.core) {
        exclude(group = "com.squareup.okhttp3")
    }
    implementation(libs.tapm.autoinit)
    debugImplementation(libs.tapm.log)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // NanoHTTPD
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // tlog
    implementation(libs.tlog)

    implementation(project(":net"))

}