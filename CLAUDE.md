# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AppInit is an Android library that distributes Application lifecycle events to different modules using APT (Annotation Processing) and Gradle Plugin. It supports AGP 8+, Kotlin 1.9+, Gradle 8.2+, and requires JDK 17.

**Minimum Android SDK**: API 21 (Android 5.0)
**Package base**: `com.nd.appinit`

## Modules

| Module | Description |
|--------|-------------|
| `appinit-annotation` | Annotation definitions: `@AppInit` |
| `appinit-runtime` | Runtime interfaces: `IAppInitListener`, `AppInitFinder`, `AppInitDispatcher` |
| `appinit-compiler` | APT annotation processor: generates `AppInitWareHouse_*` per module |
| `appinit-plugin` | Gradle plugin: composes all WareHouse classes into single `AppInitFinder` |
| `app` | Demo application (generates `AppInitFinder`) |
| `module_test` | Test library module |

## Build Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Clean project
./gradlew clean
```

## Architecture

- **IAppInitListener**: Java interface with Java 8 default methods for Application lifecycle callbacks
- **APT**: Generates `AppInitWareHouse_{moduleName}` class per module in `com.nd.appinit.processor.{moduleName}`
- **Gradle Plugin**: Uses DRouter-style transform with `project.tasks.register` + `variant.artifacts.forScope().toTransform()`
  - Scans all input jars/directories for `AppInitWareHouse_*.class` files
  - Injects bytecode via ASM into `AppInitFinder.getAllInitializers()` method
  - Full control over scan and inject execution order via custom transform task

## Plugin Configuration

The app module applies the plugin:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.nd.appinit.plugin")
}

kapt {
    arguments {
        arg("appinit.module.name", "app")
    }
}
```

Library modules should NOT apply the plugin.

## Key Interfaces

- `IAppInitListener`: Interface with default empty lifecycle methods
- `AppInitFinder`: Generated at compile-time, loads all `AppInitWareHouse` classes via reflection
- `AppInitDispatcher`: Entry point that dispatches lifecycle events to all registered listeners

## Usage Pattern

1. In Application's lifecycle methods:
   ```java
   AppInitDispatcher.dispatchOnCreate(this);
   AppInitDispatcher.dispatchAttachBaseContext(this, base);
   ```

2. Implement `IAppInitListener` with `@AppInit` annotation:
   ```java
   @AppInit(priority = 0)
   public class MyInit implements IAppInitListener {
       @Override
       public void onCreate(Application application) {
           // initialization logic
       }
   }
   ```

## Configuration Notes

- The project uses Kotlin 1.9.22 and AGP 8.2.2
- Compile SDK: 34
- Min SDK: 21 (Android 5.0)
- JVM target: 17
- The appinit-plugin uses Gradle composite build