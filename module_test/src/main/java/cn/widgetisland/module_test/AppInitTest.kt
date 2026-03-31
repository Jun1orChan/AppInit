package cn.widgetisland.module_test

import android.app.Application
import android.content.Context
import android.util.Log
import com.nd.appinit.IAppInitListener
import com.nd.appinit.annotation.AppInit

/**
 * 示例：实现 IAppInitListener 接口并用 @AppInit 标记，
 * 在 Application 生命周期中会被自动分发调用。
 */
@AppInit(priority = 10)
class AppInitTest : IAppInitListener {

    override fun onCreate(application: Application) {
        Log.i("AppInit", "AppInitTest.onCreate: ${application.packageName}")
    }

    override fun onAttachBaseContext(application: Application, baseContext: Context) {
        Log.i("AppInit", "AppInitTest.onAttachBaseContext: ${application.packageName}")
    }

    override fun onLowMemory(application: Application) {
        Log.i("AppInit", "AppInitTest.onLowMemory: ${application.packageName}")
    }
}