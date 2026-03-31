# AppInit

通过 **APT 注解** + **Gradle Plugin** 实现 Application 生命周期在各模块的分发与自动注册，支持 AGP 8+，最低支持 Android 5.0（API 21），基础包名：`com.nd.appinit`。

## 模块说明

| 模块 | 说明 |
|------|------|
| `appinit-annotation` | 注解定义：`@AppInit` |
| `appinit-runtime` | 运行时接口：`IAppInitListener`、`AppInitFinder`、`AppInitDispatcher` |
| `appinit-compiler` | APT 注解处理器：生成 `AppInitWareHouse${moduleName}` 类 |
| `appinit-plugin` | Gradle 插件：扫描并注入所有 Warehouse 类到 AppInitFinder |
| `app` | Demo 应用 |
| `module_test` | 测试库模块 |

## 使用方式

### 1. 应用模块（Application）

在 `build.gradle.kts` 中应用插件并配置 kapt：

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.nd.appinit.plugin")
}

kapt {
    arguments {
        arg("appinit.module.name", project.name)
    }
}

// 可选：禁用插件（默认为 true）
appInit {
    enabled = false
}
```

在自定义 `Application` 各生命周期中调用分发：

```java
public class App extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        AppInitDispatcher.dispatchAttachBaseContext(this, base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppInitDispatcher.dispatchOnCreate(this);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        AppInitDispatcher.dispatchOnLowMemory(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AppInitDispatcher.dispatchOnConfigurationChanged(this, newConfig);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        AppInitDispatcher.dispatchOnTerminate(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        AppInitDispatcher.dispatchOnTrimMemory(this, level);
    }
}
```

### 2. 库模块（Library）

库模块需要依赖 annotation 和 runtime，并使用 kapt：

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":appinit-runtime"))
    implementation(project(":appinit-annotation"))
    kapt(project(":appinit-compiler"))
}
```

实现 `IAppInitListener` 接口并用 `@AppInit` 标记：

```java
@AppInit(priority = 0)
public class MyInit implements IAppInitListener {

    @Override
    public void onAttachBaseContext(Application application, Context baseContext) {
        // 在 Application#attachBaseContext 时调用
    }

    @Override
    public void onCreate(Application application) {
        // 在 Application#onCreate 时调用
    }

    @Override
    public void onLowMemory(Application application) {
        // 在 Application#onLowMemory 时调用
    }

    @Override
    public void onConfigurationChanged(Application application, Configuration newConfig) {
        // 在 Application#onConfigurationChanged 时调用
    }

    @Override
    public void onTerminate(Application application) {
        // 在 Application#onTerminate 时调用（仅模拟器有效）
    }

    @Override
    public void onTrimMemory(Application application, int level) {
        // 在 Application#onTrimMemory 时调用
    }
}
```

`priority` 数值越小越先执行，默认为 `Integer.MAX_VALUE`。

## 构建

```bash
./gradlew :app:assembleDebug
```

## 技术要点

- **APT**：扫描 `@AppInit`，为每个模块生成 `AppInitWareHouse${moduleName}` 类，存放在 `com.nd.appinit.processor` 包下。
- **Gradle Plugin**：使用 `variant.artifacts.forScope().toTransform()` 自定义 Transform，扫描所有 jar 和目录中的 Warehouse 类，通过 ASM 字节码注入将调用代码合并到 `AppInitFinder.getAllInitializers()` 方法中。
- **运行时**：`AppInitDispatcher` 按 priority 排序后依次调用各生命周期方法。
- **Java 8 默认方法**：`IAppInitListener` 接口提供所有生命周期的空实现，实现类只需重写需要监听的回调。

## 配置要求

- AGP 8+
- Kotlin 1.9+
- Gradle 8.2+
- JDK 17
- Min SDK 21（Android 5.0）