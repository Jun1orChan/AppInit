# 保留 SPI 生成的注册类及接口
-keep interface com.cc.appinit.IAppInit { *; }
-keep interface com.cc.appinit.IAppInitRegistry { *; }
-keep class * implements com.cc.appinit.IAppInitRegistry { *; }
-keep class * implements com.cc.appinit.IAppInit { *; }
