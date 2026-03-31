package com.nd.appinit.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.nd.appinit.AppInitDispatcher

/**
 * 自定义 Application：在各生命周期中通过 AppInitDispatcher 向各模块分发。
 */
class App : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        base?.let {
            AppInitDispatcher.dispatchAttachBaseContext(this, it)
        }
    }

    override fun onCreate() {
        super.onCreate()
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
