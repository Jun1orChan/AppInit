package com.nd.appinit.annotation;

/**
 * Controls which process an AppInit listener should run in.
 */
public enum AppInitProcess {
    /**
     * Run in every process.
     */
    ALL,

    /**
     * Run only in the application's main process.
     */
    MAIN
}
