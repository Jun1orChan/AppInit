package com.nd.appinit;

import com.nd.appinit.annotation.AppInitProcess;

/**
 * @author cwj
 * @date 2026/3/31 17:51
 */
public class AppInitInfo {

    public IAppInitListener appInitListener;
    public int priority = Integer.MAX_VALUE;
    public AppInitProcess process = AppInitProcess.ALL;

    public AppInitInfo(IAppInitListener appInitListener, int priority, AppInitProcess process) {
        this.appInitListener = appInitListener;
        this.priority = priority;
        this.process = process;
    }

    public AppInitInfo() {
    }

}
