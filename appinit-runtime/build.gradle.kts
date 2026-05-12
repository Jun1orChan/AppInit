plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = rootProject.file("gradle/publish.gradle"))
android {
    namespace = "com.nd.appinit.runtime"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
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
