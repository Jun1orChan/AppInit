package com.nd.appinit;

import android.app.Application;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.nd.appinit.annotation.AppInitProcess;

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
    private static volatile String currentProcessName;
    private static volatile boolean debugLogEnabled;

    private AppInitDispatcher() {
    }

    /**
     * 控制 AppInitDispatcher 的调试日志，开启后会输出各初始化监听器的生命周期耗时。
     */
    public static void setDebugLogEnabled(boolean enabled) {
        debugLogEnabled = enabled;
    }

    public static boolean isDebugLogEnabled() {
        return debugLogEnabled;
    }

    /**
     * 在 Application#attachBaseContext 中调用，向各模块分发 attachBaseContext。
     */
    public static void dispatchAttachBaseContext(Application application, Context baseContext) {
        for (IAppInitListener listener : loadListeners(application)) {
            long startTime = markStart("attachBaseContext", listener);
            try {
                listener.onAttachBaseContext(application, baseContext);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch attachBaseContext: " + listener.getClass().getName(), e);
            } finally {
                logCost("attachBaseContext", listener, startTime);
            }
        }
    }

    /**
     * 在 Application#onCreate 中调用，向各模块分发 onCreate。
     */
    public static void dispatchOnCreate(Application application) {
        for (IAppInitListener listener : loadListeners(application)) {
            long startTime = markStart("onCreate", listener);
            try {
                listener.onCreate(application);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch onCreate: " + listener.getClass().getName(), e);
            } finally {
                logCost("onCreate", listener, startTime);
            }
        }
    }

    /**
     * 在 Application#onLowMemory 中调用，向各模块分发 onLowMemory。
     */
    public static void dispatchOnLowMemory(Application application) {
        for (IAppInitListener listener : loadListeners(application)) {
            long startTime = markStart("onLowMemory", listener);
            try {
                listener.onLowMemory(application);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch onLowMemory: " + listener.getClass().getName(), e);
            } finally {
                logCost("onLowMemory", listener, startTime);
            }
        }
    }

    /**
     * 在 Application#onConfigurationChanged 中调用，向各模块分发 onConfigurationChanged。
     */
    public static void dispatchOnConfigurationChanged(Application application, Configuration newConfig) {
        for (IAppInitListener listener : loadListeners(application)) {
            long startTime = markStart("onConfigurationChanged", listener);
            try {
                listener.onConfigurationChanged(application, newConfig);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch onConfigurationChanged: " + listener.getClass().getName(), e);
            } finally {
                logCost("onConfigurationChanged", listener, startTime);
            }
        }
    }

    /**
     * 在 Application#onTerminate 中调用，向各模块分发 onTerminate。
     */
    public static void dispatchOnTerminate(Application application) {
        for (IAppInitListener listener : loadListeners(application)) {
            long startTime = markStart("onTerminate", listener);
            try {
                listener.onTerminate(application);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch onTerminate: " + listener.getClass().getName(), e);
            } finally {
                logCost("onTerminate", listener, startTime);
            }
        }
    }

    /**
     * 在 Application#onTrimMemory 中调用，向各模块分发 onTrimMemory。
     */
    public static void dispatchOnTrimMemory(Application application, int level) {
        for (IAppInitListener listener : loadListeners(application)) {
            long startTime = markStart("onTrimMemory", listener);
            try {
                listener.onTrimMemory(application, level);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch onTrimMemory: " + listener.getClass().getName(), e);
            } finally {
                logCost("onTrimMemory", listener, startTime);
            }
        }
    }

    private static List<IAppInitListener> loadListeners(Application application) {
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
                List<AppInitInfo> classes = new ArrayList<>(AppInitFinder.getAllInitializers());
                //排序，小的数字在前
                Collections.sort(classes, new Comparator<AppInitInfo>() {
                    @Override
                    public int compare(AppInitInfo o1, AppInitInfo o2) {
                        return Integer.compare(o1.priority, o2.priority);
                    }
                });
                for (AppInitInfo pair : classes) {
                    if (pair.appInitListener != null && shouldRunInCurrentProcess(application, pair)) {
                        listeners.add(pair.appInitListener);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load initializers from AppInit: " + e.getMessage(), e);
            }
            sortedListeners = listeners;
            return sortedListeners;
        }
    }

    private static boolean shouldRunInCurrentProcess(Application application, AppInitInfo info) {
        if (info.process == null || info.process == AppInitProcess.ALL) {
            return true;
        }
        if (info.process == AppInitProcess.MAIN) {
            return application.getPackageName().equals(getCurrentProcessName(application));
        }
        return false;
    }

    private static String getCurrentProcessName(Application application) {
        if (currentProcessName != null) {
            return currentProcessName;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            currentProcessName = Application.getProcessName();
            return currentProcessName;
        }

        int pid = Process.myPid();
        Object service = application.getSystemService(Context.ACTIVITY_SERVICE);
        if (service instanceof ActivityManager) {
            List<ActivityManager.RunningAppProcessInfo> processes = ((ActivityManager) service).getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo processInfo : processes) {
                    if (processInfo.pid == pid) {
                        currentProcessName = processInfo.processName;
                        return currentProcessName;
                    }
                }
            }
        }

        currentProcessName = application.getPackageName();
        return currentProcessName;
    }

    private static long markStart(String event, IAppInitListener listener) {
        if (!debugLogEnabled) {
            return 0L;
        }
        return System.currentTimeMillis();
    }

    private static void logCost(String event, IAppInitListener listener, long startTime) {
        if (startTime == 0L || !debugLogEnabled) {
            return;
        }
        Log.d(TAG, listener.getClass().getName() + "#" + event + " cost " + (System.currentTimeMillis() - startTime) + "ms");
    }
}
