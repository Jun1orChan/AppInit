package com.nd.appinit;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;

/**
 * Application 生命周期监听器接口。
 * 使用 Java 8 默认方法特性，提供所有生命周期的空实现。
 * 实现类只需重写需要监听的回调即可。
 *
 * 使用方式：
 * 1. 在实现类上标注 {@link com.nd.appinit.annotation.AppInit} 注解
 * 2. 在 Application 生命周期中通过 {@link AppInitDispatcher} 统一分发
 */
public interface IAppInitListener {

    /**
     * 在 Application#attachBaseContext 中调用。
     * 此时 Application 对象已创建但 onCreate 尚未调用。
     *
     * @param application Application 实例
     * @param baseContext 原始的 baseContext
     */
    default void onAttachBaseContext(Application application, Context baseContext) {
    }

    /**
     * 在 Application#onCreate 中调用。
     *
     * @param application Application 实例
     */
    default void onCreate(Application application) {
    }

    /**
     * 在 Application#onLowMemory 中调用。
     *
     * @param application Application 实例
     */
    default void onLowMemory(Application application) {
    }

    /**
     * 在 Application#onConfigurationChanged 中调用。
     *
     * @param application Application 实例
     * @param newConfig 新的配置
     */
    default void onConfigurationChanged(Application application, Configuration newConfig) {
    }

    /**
     * 在 Application#onTerminate 中调用。
     *
     * @param application Application 实例
     */
    default void onTerminate(Application application) {
    }

    /**
     * 在 Application#onTrimMemory 中调用。
     *
     * @param application Application 实例
     * @param level 内存级别，见 {@link ComponentCallbacks2}
     */
    default void onTrimMemory(Application application, int level) {
    }
}