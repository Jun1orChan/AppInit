# AppInit

通过 **APT 注解** + **Gradle Plugin** 实现 Android `Application` 生命周期在多模块间的自动注册与分发。

各业务模块只需要声明自己的初始化类，宿主 `Application` 只调用统一分发入口。框架会在编译期收集所有模块的初始化类，并通过 ASM 注入到运行时查找器中。

基础包名：`com.nd.appinit`

## 模块说明

| 模块 | 说明 |
|------|------|
| `appinit-annotation` | 注解定义：`@AppInit`、`AppInitProcess` |
| `appinit-runtime` | 运行时接口与分发器：`IAppInitListener`、`AppInitDispatcher` |
| `appinit-compiler` | APT 注解处理器：为每个模块生成 `AppInitWareHouse$moduleName` |
| `appinit-plugin` | Gradle 插件：扫描 Warehouse 类并通过 ASM 注入 `AppInitFinder` |
| `app` | Demo 应用 |
| `module_test` | Demo 库模块 |

`AppInitFinder` 是 runtime 内部实现类，不作为对外 API 使用。

## 使用方式

### 1. 应用模块

应用模块需要应用插件，并配置 kapt 参数：

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

appInit {
    enabled = true
    verbose = false
    failOnMissingFinder = true
}
```

插件会在应用了 `com.android.application` 的模块中生效，不要求模块名必须是 `app`。

`appInit` 配置：

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | `true` | 是否启用 AppInit transform |
| `verbose` | `false` | 是否打印每个 jar/目录的扫描日志 |
| `failOnMissingFinder` | `true` | 扫到 Warehouse 但找不到 runtime 注入点时是否让构建失败 |

在自定义 `Application` 中调用分发：

```kotlin
class App : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        base?.let {
            AppInitDispatcher.dispatchAttachBaseContext(this, it)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppInitDispatcher.setDebugLogEnabled(BuildConfig.DEBUG)
        AppInitDispatcher.dispatchOnCreate(this)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppInitDispatcher.dispatchOnLowMemory(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppInitDispatcher.dispatchOnConfigurationChanged(this, newConfig)
    }

    override fun onTerminate() {
        super.onTerminate()
        AppInitDispatcher.dispatchOnTerminate(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppInitDispatcher.dispatchOnTrimMemory(this, level)
    }
}
```

`AppInitDispatcher.setDebugLogEnabled(true)` 会输出各初始化类生命周期回调耗时，默认关闭。

### 2. 库模块

库模块需要依赖 annotation、runtime，并使用 kapt：

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

kapt {
    arguments {
        arg("appinit.module.name", project.name)
    }
}

dependencies {
    implementation(project(":appinit-annotation"))
    implementation(project(":appinit-runtime"))
    kapt(project(":appinit-compiler"))
}
```

实现 `IAppInitListener` 并使用 `@AppInit` 标记：

```kotlin
@AppInit(priority = 10, process = AppInitProcess.MAIN)
class MyInit : IAppInitListener {

    override fun onAttachBaseContext(application: Application, baseContext: Context) {
        // Application#attachBaseContext
    }

    override fun onCreate(application: Application) {
        // Application#onCreate
    }
}
```

`IAppInitListener` 的所有方法都有默认空实现，只需要重写当前模块关心的生命周期。

## 注解参数

```java
@AppInit(
    priority = 0,
    process = AppInitProcess.ALL
)
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `priority` | `Integer.MAX_VALUE` | 数值越小越先执行 |
| `process` | `AppInitProcess.ALL` | 控制初始化类在哪些进程执行 |

`AppInitProcess`：

| 值 | 说明 |
|----|------|
| `ALL` | 所有进程都执行 |
| `MAIN` | 仅主进程执行，主进程判断规则为 `currentProcessName == application.packageName` |

APT 会在编译期校验：

- `@AppInit` 只能标记 class
- 被标记的类必须实现 `IAppInitListener`
- 被标记的类必须有 public 无参构造，或没有显式声明构造函数

`appinit.module.name` 会被转换成合法 Java 类名片段。例如 `feature-user` 会生成：

```text
AppInitWareHouse$feature_user
```

## 构建

```bash
./gradlew :app:assembleDebug
```

## 技术要点

- **APT**：扫描 `@AppInit`，为每个模块生成 `com.nd.appinit.processor.AppInitWareHouse$moduleName`。
- **生成代码**：Warehouse 直接 `new` 初始化类并生成 `AppInitInfo`，运行时不再通过反射创建 listener。
- **Gradle Plugin**：使用 AGP 8 的 `variant.artifacts.forScope(...).toTransform(...)` 扫描所有 jar 和目录中的 Warehouse 类。
- **ASM 注入**：插件完整替换 runtime 内部 `AppInitFinder.getAllInitializers()` 方法体，将所有 Warehouse 的结果合并到内部缓存。
- **运行时分发**：`AppInitDispatcher` 复制 Finder 返回的不可变列表，按 `priority` 升序排序，并按进程配置过滤后分发生命周期。
- **扫描策略**：当前每次 transform 都基于当前输入全量扫描，避免删除 `@AppInit` 后缓存残留旧 Warehouse。
- **混淆规则**：`appinit-runtime` 自带 consumer ProGuard rules，保留生成的 Warehouse 入口。

## 配置要求

- AGP 8+
- Kotlin 1.9+
- Gradle 8.2+
- JDK 17
- Min SDK 21（Android 5.0）
