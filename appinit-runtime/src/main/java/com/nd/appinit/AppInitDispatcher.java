package com.nd.appinit;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Application 生命周期分发器。
 * 通过编译期 APT 生成的 {@link AppInitFinder#getAllInitializers()} 获取所有模块的监听器，
 * 合并后按优先级依次调用 {@link IAppInitListener} 的各个生命周期方法。
 *
 * 使用方式：在自定义 Application 生命周期中调用对应方法
 * <pre>
 * public class App extends Application {
 *     @Override
 *     protected void attachBaseContext(Context base) {
 *         super.attachBaseContext(base);
 *         AppInitDispatcher.dispatchAttachBaseContext(this, base);
 *     }
 *
 *     @Override
 *     public void onCreate() {
 *         super.onCreate();
 *         AppInitDispatcher.dispatchOnCreate(this);
 *     }
 *
 *     @Override
 *     public void onLowMemory() {
 *         super.onLowMemory();
 *         AppInitDispatcher.dispatchOnLowMemory(this);
 *     }
 *
 *     @Override
 *     public void onConfigurationChanged(Configuration newConfig) {
 *         super.onConfigurationChanged(newConfig);
 *         AppInitDispatcher.dispatchOnConfigurationChanged(this, newConfig);
 *     }
 *
 *     @Override
 *     public void onTerminate() {
 *         super.onTerminate();
 *         AppInitDispatcher.dispatchOnTerminate(this);
 *     }
 *
 *     @Override
 *     public void onTrimMemory(int level) {
 *         super.onTrimMemory(level);
 *         AppInitDispatcher.dispatchOnTrimMemory(this, level);
 *     }
 * }
 * </pre>
 */
public final class AppInitDispatcher {

    private static final String TAG = "AppInitDispatcher";

    private static volatile List<IAppInitListener> sortedListeners;

    private AppInitDispatcher() {
    }

    /**
     * 在 Application#attachBaseContext 中调用，向各模块分发 attachBaseContext。
     */
    public static void dispatchAttachBaseContext(Application application, Context baseContext) {
        for (IAppInitListener listener : loadListeners()) {
            try {
                listener.onAttachBaseContext(application, baseContext);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch attachBaseContext: " + listener.getClass().getName(), e);
            }
        }
    }

    /**
     * 在 Application#onCreate 中调用，向各模块分发 onCreate。
     */
    public static void dispatchOnCreate(Application application) {
        for (IAppInitListener listener : loadListeners()) {
            try {
                listener.onCreate(application);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch onCreate: " + listener.getClass().getName(), e);
            }
        }
    }

    /**
     * 在 Application#onLowMemory 中调用，向各模块分发 onLowMemory。
     */
    public static void dispatchOnLowMemory(Application application) {
        for (IAppInitListener listener : loadListeners()) {
            try {
                listener.onLowMemory(application);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch onLowMemory: " + listener.getClass().getName(), e);
            }
        }
    }

    /**
     * 在 Application#onConfigurationChanged 中调用，向各模块分发 onConfigurationChanged。
     */
    public static void dispatchOnConfigurationChanged(Application application, Configuration newConfig) {
        for (IAppInitListener listener : loadListeners()) {
            try {
                listener.onConfigurationChanged(application, newConfig);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch onConfigurationChanged: " + listener.getClass().getName(), e);
            }
        }
    }

    /**
     * 在 Application#onTerminate 中调用，向各模块分发 onTerminate。
     */
    public static void dispatchOnTerminate(Application application) {
        for (IAppInitListener listener : loadListeners()) {
            try {
                listener.onTerminate(application);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch onTerminate: " + listener.getClass().getName(), e);
            }
        }
    }

    /**
     * 在 Application#onTrimMemory 中调用，向各模块分发 onTrimMemory。
     */
    public static void dispatchOnTrimMemory(Application application, int level) {
        for (IAppInitListener listener : loadListeners()) {
            try {
                listener.onTrimMemory(application, level);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch onTrimMemory: " + listener.getClass().getName(), e);
            }
        }
    }

    private static List<IAppInitListener> loadListeners() {
        if (sortedListeners != null) {
            return sortedListeners;
        }

        synchronized (AppInitDispatcher.class) {
            if (sortedListeners != null) {
                return sortedListeners;
            }

            List<IAppInitListener> listeners = new ArrayList<>();

            try {
                // 通过编译期生成的 AppInitFinder 类获取所有监听器类
                List<AppInitInfo> classes = AppInitFinder.getAllInitializers();
                //排序，小的数字在前
                Collections.sort(classes, new Comparator<AppInitInfo>() {
                    @Override
                    public int compare(AppInitInfo o1, AppInitInfo o2) {
                        return o1.priority - o2.priority;
                    }
                });
                for (AppInitInfo pair : classes) {
                    IAppInitListener instance = createInstance(pair.appInitListenerClass);
                    if (instance != null) {
                        listeners.add(instance);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load initializers from AppInit: " + e.getMessage(), e);
            }
            sortedListeners = listeners;
            return sortedListeners;
        }
    }

    private static IAppInitListener createInstance(Class<? extends IAppInitListener> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            Log.e(TAG, "Failed to create instance: " + clazz.getName(), e);
            return null;
        }
    }
}