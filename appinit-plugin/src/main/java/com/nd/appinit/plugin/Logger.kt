package com.nd.appinit.plugin

/**
 * Logger utility for AppInit plugin
 */
object Logger {
    private const val TAG = "AppInitPlugin"

    fun i(message: String) {
        println("[$TAG] $message")
    }

    fun e(message: String) {
        System.err.println("[$TAG] ERROR: $message")
    }
}