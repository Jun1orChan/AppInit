package com.nd.appinit;


import java.util.ArrayList;
import java.util.List;

/**
 * Application 生命周期初始化查找器。
 * 由 Gradle 插件在编译期自动注入实现。
 */
public final class AppInitFinder {

    private static List<AppInitInfo> INITIALIZERS;

    /**
     * 获取所有模块的 IAppInitListener 实现类列表。
     * 由编译期插件自动注入。
     */
    public static List<AppInitInfo> getAllInitializers() {
        if (INITIALIZERS == null) {
            INITIALIZERS = new ArrayList<>();
            // 由编译期 ASM 注入代码填充
        }
        return INITIALIZERS;
    }
}