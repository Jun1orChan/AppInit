package com.nd.appinit.plugin

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import java.io.File
import java.util.jar.JarFile

/**
 * Scans for AppInitWareHouse classes from jars and directories
 */
object WarehouseScanner {

    fun loadFromCacheOrScan(
        cacheFile: File,
        allJars: List<RegularFile>,
        allDirectories: List<Directory>
    ): List<String> {
        if (!cacheFile.exists()) {
            return scanAllInputs(allJars, allDirectories)
        }

        try {
            val lines = cacheFile.readLines()
            if (lines.isEmpty()) {
                return scanAllInputs(allJars, allDirectories)
            }

            val cachedWarehouseClasses = lines[0].split(",").filter { it.isNotBlank() }.toMutableList()

            // Parse cached jar inputs
            val cachedInputs = lines.drop(1).associate { line ->
                val parts = line.split("|")
                if (parts.size == 2) parts[0] to parts[1].toLongOrNull() else "" to 0L
            }.toMutableMap()

            // Check which jars changed
            val changedJars = mutableListOf<RegularFile>()
            val unchangedJars = mutableListOf<RegularFile>()

            for (jarFile in allJars) {
                val cachedTime = cachedInputs[jarFile.asFile.absolutePath]
                if (cachedTime == null || cachedTime != jarFile.asFile.lastModified()) {
                    changedJars.add(jarFile)
                } else {
                    unchangedJars.add(jarFile)
                }
            }
            // Changed jars found - scan changed jars + directories
            Logger.i("[CACHE] Changed: ${changedJars.size} jars, scan ${allDirectories.size} dirs, use cache: ${unchangedJars.size} jars")
            val result = mutableListOf<String>()
            result.addAll(cachedWarehouseClasses)

            // Scan changed jars
            for (jarFile in changedJars) {
                Logger.i("[SCAN] changedJar: ${jarFile.asFile.absolutePath}")
                val jar = JarFile(jarFile.asFile)
                for (entry in jar.entries()) {
                    if (entry.name.startsWith("com/nd/appinit/processor/") && entry.name.contains("AppInitWareHouse$") && entry.name.endsWith(".class")) {
                        val className = entry.name.replace("/", ".").removeSuffix(".class")
                        if (!result.contains(className)) {
                            result.add(className)
                            Logger.i("[SCAN] $className in jar: ${jar.name}")
                        }
                    }
                }
                jar.close()
            }

            // Scan directories
            for (dir in allDirectories) {
                Logger.i("[SCAN] dir: $dir")
                val classesDir = File(dir.asFile, "com/nd/appinit/processor")
                if (classesDir.exists() && classesDir.isDirectory) {
                    classesDir.walkTopDown()
                        .filter { it.isFile && it.name.contains("AppInitWareHouse") && it.name.endsWith(".class") }
                        .forEach { file ->
                            val relativePath = file.relativeTo(classesDir).path.replace("/", ".")
                            val className = "com.nd.appinit.processor.${relativePath.removeSuffix(".class")}"
                            if (!result.contains(className)) {
                                result.add(className)
                                Logger.i("[SCAN] $className in dir: $classesDir")
                            }
                        }
                }
            }

            return result
        } catch (e: Exception) {
            return scanAllInputs(allJars, allDirectories)
        }
    }

    fun saveCache(
        warehouseClasses: List<String>,
        allJars: List<RegularFile>,
        cacheFile: File
    ) {
        cacheFile.parentFile?.mkdirs()
        val lines = mutableListOf<String>()
        lines.add(warehouseClasses.joinToString(","))

        // Save only jar timestamps
        for (jarFile in allJars) {
            lines.add("${jarFile.asFile.absolutePath}|${jarFile.asFile.lastModified()}")
        }

        cacheFile.writeText(lines.joinToString("\n"))
    }

    private fun scanAllInputs(
        allJars: List<RegularFile>,
        allDirectories: List<Directory>
    ): List<String> {
        val warehouseClasses = mutableListOf<String>()

        // Scan all jars
        for (jarFile in allJars) {
            Logger.i("[SCAN] jar: ${jarFile.asFile.absolutePath}")
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
            Logger.i("[SCAN] dir: $dir")
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