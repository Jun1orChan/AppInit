plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

group = "com.nd.appinit"
version = "1.0.0"

android {
    namespace = "com.nd.appinit.runtime"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
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
    implementation(project(":appinit-annotation"))
}