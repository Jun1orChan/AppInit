package com.nd.appinit.app.init

import android.app.Application
import android.util.Log
import com.nd.appinit.annotation.AppInit
import com.nd.appinit.IAppInitListener

/**
 * 示例：实现 IAppInitListener 接口并用 @AppInit 标记，
 * 在 Application 生命周期中会被自动分发调用。
 */
@AppInit(priority = 100)
class AppInitDemo : IAppInitListener {

    override fun onCreate(application: Application) {
        Log.i("AppInit", "AppInitDemo.onCreate: ${application.packageName}")
    }

    override fun onAttachBaseContext(application: Application, baseContext: android.content.Context) {
        Log.i("AppInit", "AppInitDemo.onAttachBaseContext: ${application.packageName}")
    }

    override fun onLowMemory(application: Application) {
        Log.i("AppInit", "AppInitDemo.onLowMemory: ${application.packageName}")
    }
}
