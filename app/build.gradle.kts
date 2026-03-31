import com.android.build.gradle.ProguardFiles.getDefaultProguardFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.nd.appinit.plugin")
}

android {
    namespace = "com.nd.appinit.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nd.appinit.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("appinit.module.name", project.name)
    }
}

appInit {
    enabled = false
}

dependencies {
    implementation(project(":appinit-runtime"))
    implementation(project(":appinit-annotation"))
    kapt(project(":appinit-compiler"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation(project(":module_test"))
}