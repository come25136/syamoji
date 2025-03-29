import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "id.come25136.syamoji"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.come25136.syamoji"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "REVISION", "\"${getGitCommitId()}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "REVISION", "\"${getGitCommitId()}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":library"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.glide)

    implementation(libs.rxandroid)
    implementation(libs.rxjava)
    implementation(libs.androidx.leanback)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.rxjava.v310)
    implementation(libs.rxandroid)
    implementation(libs.adapter.rxjava3)

    implementation(libs.simple.xml)
    implementation(libs.okhttp)
    implementation(libs.androidx.espresso.core)

    implementation(libs.m3u.parser)
    implementation(libs.androidx.media3.session)

    implementation("org.videolan.android:libvlc-all:4.0.0-eap15")
}

fun getGitCommitId(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}


