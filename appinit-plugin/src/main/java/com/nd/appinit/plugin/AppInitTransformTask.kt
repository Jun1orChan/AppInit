package com.nd.appinit.plugin

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import javax.inject.Inject

/**
 * Custom transform task that scans for warehouse classes
 */
abstract class AppInitTransformTask @Inject constructor(
    private val androidComponents: AndroidComponentsExtension<*, *, *>
) : DefaultTask() {

    @get:InputFiles
    abstract val allJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val allDirectories: ListProperty<Directory>

    @get:OutputFile
    abstract val output: RegularFileProperty

    private val inputFiles = mutableSetOf<File>()
    private val cacheDir = File(project.buildDir, "appinit-cache")

    private fun getCacheFile(): File {
        val variant = name.removePrefix("Debug").removePrefix("Release").lowercase()
        return File(cacheDir, "scan-cache-${variant}.txt")
    }

    @TaskAction
    fun taskAction() {
        val timeStart = System.currentTimeMillis()
        Logger.i("[TASK] AppInitTransform start")

        // Step 1: Scan warehouse classes
        val warehouseClasses = WarehouseScanner.loadFromCacheOrScan(
            getCacheFile(), allJars.get(), allDirectories.get()
        )
        Logger.i("[TASK] Found ${warehouseClasses.size} warehouse classes: $warehouseClasses")

        // Step 2: Inject into AppInitFinder
        val modifications = AsmInjector.transformAppInitFinder(
            allJars.get(), allDirectories.get(), warehouseClasses
        )

        // Step 3: Pack output jar
        packOutputJar(modifications)

        // Step 4: Save cache
        WarehouseScanner.saveCache(warehouseClasses, allJars.get(), getCacheFile())

        Logger.i("[TASK] AppInitTransform done, time used: ${System.currentTimeMillis() - timeStart}ms")
    }

    private fun packOutputJar(modifications: Map<File, ByteArray?>) {
        val outputFile = output.get().asFile
        outputFile.parentFile?.mkdirs()

        val jarOutput = JarOutputStream(BufferedOutputStream(FileOutputStream(outputFile)))
        val insertedEntries = mutableSetOf<String>()
        inputFiles.clear()

        // Collect all input files
        allJars.get().forEach { inputFiles.add(it.asFile) }
        allDirectories.get().forEach { inputFiles.add(it.asFile) }

        // Copy all inputs to output jar
        inputFiles.forEach { input ->
            if (input.isFile && input.name.lowercase().endsWith(".jar")) {
                val modifiedBytes = modifications[input]
                if (modifiedBytes != null) {
                    writeModifiedJar(jarOutput, input, modifiedBytes, insertedEntries)
                } else {
                    copyJar(jarOutput, input, insertedEntries)
                }
            } else if (input.isDirectory) {
                copyDir(jarOutput, input, insertedEntries)
            }
        }

        jarOutput.close()
        Logger.i("[PACK] Output jar: ${outputFile.absolutePath}")
    }

    private fun copyJar(
        jarOutput: JarOutputStream,
        jarFile: File,
        insertedEntries: MutableSet<String>
    ) {
        val jar = JarFile(jarFile)
        for (entry in jar.entries()) {
            if (!insertedEntries.contains(entry.name)) {
                insertedEntries.add(entry.name)
                jarOutput.putNextEntry(JarEntry(entry.name))
                jar.getInputStream(entry).use { it.copyTo(jarOutput) }
                jarOutput.closeEntry()
            }
        }
        jar.close()
    }

    private fun writeModifiedJar(
        jarOutput: JarOutputStream,
        originalJar: File,
        modifiedBytes: ByteArray,
        insertedEntries: MutableSet<String>
    ) {
        val jar = JarFile(originalJar)
        for (entry in jar.entries()) {
            if (!insertedEntries.contains(entry.name)) {
                insertedEntries.add(entry.name)
                jarOutput.putNextEntry(JarEntry(entry.name))
                if (entry.name == "com/nd/appinit/AppInitFinder.class") {
                    jarOutput.write(modifiedBytes)
                } else {
                    jar.getInputStream(entry).use { it.copyTo(jarOutput) }
                }
                jarOutput.closeEntry()
            }
        }
        jar.close()
    }

    private fun copyDir(
        jarOutput: JarOutputStream,
        dir: File,
        insertedEntries: MutableSet<String>
    ) {
        dir.walk().filter { it.isFile }.forEach { file ->
            val relativePath = dir.toURI().relativize(file.toURI()).path
            if (!insertedEntries.contains(relativePath)) {
                insertedEntries.add(relativePath)
                jarOutput.putNextEntry(JarEntry(relativePath.replace(File.separatorChar, '/')))
                file.inputStream().use { it.copyTo(jarOutput) }
                jarOutput.closeEntry()
            }
        }
    }
}