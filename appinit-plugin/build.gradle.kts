plugins {
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("maven-publish")
}

group = "com.nd.appinit"
version = "1.0.0"

gradlePlugin {
    plugins {
        register("appinit") {
            id = "com.nd.appinit.plugin"
            implementationClass = "com.nd.appinit.plugin.AppInitPlugin"
        }
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.2.2")
    compileOnly("com.android.tools.build:gradle-api:8.2.2")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    implementation("org.ow2.asm:asm-util:9.7")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("AppInit Plugin")
                description.set("Android AppInit Gradle Plugin")
            }
        }
    }
    repositories {
        maven {
            url = uri("${System.getProperty("user.home")}/.m2/repository")
            name = "LocalMaven"
        }
    }
}