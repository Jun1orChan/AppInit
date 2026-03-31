package com.nd.appinit;

/**
 * @author cwj
 * @date 2026/3/31 17:51
 */
public class AppInitInfo {

    public Class<? extends IAppInitListener> appInitListenerClass;
    public int priority = Integer.MAX_VALUE;

    public AppInitInfo(Class<? extends IAppInitListener> appInitListenerClass, int priority) {
        this.appInitListenerClass = appInitListenerClass;
        this.priority = priority;
    }

    public AppInitInfo() {
    }

}