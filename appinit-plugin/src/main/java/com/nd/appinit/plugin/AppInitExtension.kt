package com.nd.appinit.plugin

/**
 * AppInit 插件扩展配置。
 */
abstract class AppInitExtension {

    var enabled: Boolean = true

    var verbose: Boolean = false

    var failOnMissingFinder: Boolean = true
}
