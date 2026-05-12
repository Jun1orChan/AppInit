package com.nd.appinit.plugin

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import java.io.File
import java.util.jar.JarFile

/**
 * Scans for AppInitWareHouse classes from jars and directories
 */
object WarehouseScanner {

    fun scanAllInputs(
        allJars: List<RegularFile>,
        allDirectories: List<Directory>,
        verbose: Boolean
    ): List<String> {
        val warehouseClasses = mutableListOf<String>()

        // Scan all jars
        for (jarFile in allJars) {
            if (verbose) {
                Logger.i("[SCAN] jar: ${jarFile.asFile.absolutePath}")
            }
            val jar = JarFile(jarFile.asFile)
            for (entry in jar.entries()) {
                if (entry.name.startsWith("com/nd/appinit/processor/") && entry.name.contains("AppInitWareHouse") && entry.name.endsWith(".class")) {
                    val className = entry.name.replace("/", ".").removeSuffix(".class")
                    if (!warehouseClasses.contains(className)) {
                        warehouseClasses.add(className)
                        Logger.i("[SCAN] $className in jar: ${jar.name}")
                    }
                }
            }
            jar.close()
        }

        // Scan directories
        for (dir in allDirectories) {
            if (verbose) {
                Logger.i("[SCAN] dir: $dir")
            }
            val classesDir = File(dir.asFile, "com/nd/appinit/processor")
            if (classesDir.exists() && classesDir.isDirectory) {
                classesDir.walkTopDown()
                    .filter { it.isFile && it.name.contains("AppInitWareHouse") && it.name.endsWith(".class") }
                    .forEach { file ->
                        val relativePath = file.relativeTo(classesDir).path.replace("/", ".")
                        val className = "com.nd.appinit.processor.${relativePath.removeSuffix(".class")}"
                        if (!warehouseClasses.contains(className)) {
                            warehouseClasses.add(className)
                            Logger.i("[SCAN] $className in dir: $classesDir")
                        }
                    }
            }
        }

        Logger.i("[SCAN] Total: ${warehouseClasses.size} warehouse classes")
        return warehouseClasses
    }
}
