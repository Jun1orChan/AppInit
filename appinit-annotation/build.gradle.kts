plugins {
    id("java-library")
}
apply(from = rootProject.file("gradle/publish.gradle"))

group = "com.nd.appinit"
version = "1.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}