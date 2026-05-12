package com.nd.appinit.plugin

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Handle
import org.gradle.api.GradleException
import java.io.File
import java.util.jar.JarFile

/**
 * ASM injector for AppInitFinder bytecode manipulation
 */
object AsmInjector {

    /**
     * Transform AppInitFinder class in all jars and directories
     * @return map of modified file -> modified bytes
     */
    fun transformAppInitFinder(
        allJars: List<RegularFile>,
        allDirectories: List<Directory>,
        warehouseClasses: List<String>,
        failOnMissingFinder: Boolean
    ): Map<File, ByteArray> {
        if (warehouseClasses.isEmpty()) {
            Logger.i("[INJECT] No warehouse classes found, skip AppInitFinder transform")
            return emptyMap()
        }

        val modifications = mutableMapOf<File, ByteArray?>()
        var finderFound = false

        // Process all jars
        allJars.forEach { jarFile ->
            val jar = JarFile(jarFile.asFile)
            for (entry in jar.entries()) {
                if (entry.name == "com/nd/appinit/AppInitFinder.class") {
                    finderFound = true
                    val bytes = jar.getInputStream(entry).readBytes()
                    val modified = injectIntoAppInitFinder(bytes, warehouseClasses)
                    modifications[jarFile.asFile] = modified
                    Logger.i("[INJECT] Modified AppInitFinder from jar: ${jarFile.asFile.absolutePath}")
                }
            }
            jar.close()
        }

        // Process directories
        allDirectories.forEach { dir ->
            val finderFile = File(dir.asFile, "com/nd/appinit/AppInitFinder.class")
            if (finderFile.exists()) {
                finderFound = true
                val bytes = finderFile.readBytes()
                val modified = injectIntoAppInitFinder(bytes, warehouseClasses)
                modifications[dir.asFile] = modified
                Logger.i("[INJECT] Modified AppInitFinder in dir: ${dir.asFile}")
            }
        }

        if (!finderFound && failOnMissingFinder) {
            throw GradleException(
                "AppInitFinder.class not found. Please add appinit-runtime as an implementation dependency in the application module."
            )
        }

        return modifications.filterValues { it != null }.mapValues { it.value!! }
    }

    private fun injectIntoAppInitFinder(bytes: ByteArray, warehouseClasses: List<String>): ByteArray {
        try {
            val reader = ClassReader(bytes)
            val writer = ClassWriter(reader, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
            var transformed = false

            val visitor = object : ClassVisitor(Opcodes.ASM9, writer) {
                private var currentClassName = ""
                private var processed = false

                override fun visit(
                    version: Int,
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    currentClassName = name.replace("/", ".")
                    super.visit(version, access, name, signature, superName, interfaces)
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor? {
                    if (currentClassName == "com.nd.appinit.AppInitFinder"
                        && name == "getAllInitializers"
                        && descriptor == "()Ljava/util/List;"
                        && !processed
                    ) {
                        processed = true
                        transformed = true
                        Logger.i("[INJECT] Transforming getAllInitializers with ${warehouseClasses.size} warehouses")

                        val originalMv = cv.visitMethod(access, name, descriptor, signature, exceptions)
                        return ReplaceMethodVisitor(originalMv, warehouseClasses)
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions)
                }
            }

            reader.accept(visitor, 0)
            if (!transformed) {
                throw GradleException("Failed to find AppInitFinder.getAllInitializers() for bytecode replacement.")
            }
            return writer.toByteArray()
        } catch (e: Exception) {
            throw GradleException("Failed to inject AppInitFinder: ${e.message}", e)
        }
    }

    /**
     * ASM MethodVisitor that replaces AppInitFinder.getAllInitializers().
     */
    class ReplaceMethodVisitor(
        private val originalMv: MethodVisitor,
        private val warehouseClasses: List<String>
    ) : MethodVisitor(Opcodes.ASM9, originalMv) {

        private var generated = false

        override fun visitCode() {
            originalMv.visitCode()
            if (!generated) {
                // if (INITIALIZERS == null) { INITIALIZERS = new ArrayList(); ... }
                originalMv.visitFieldInsn(Opcodes.GETSTATIC, "com/nd/appinit/AppInitFinder", "INITIALIZERS", "Ljava/util/List;")
                val initialized = Label()
                originalMv.visitJumpInsn(Opcodes.IFNONNULL, initialized)
                originalMv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
                originalMv.visitInsn(Opcodes.DUP)
                originalMv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
                originalMv.visitFieldInsn(Opcodes.PUTSTATIC, "com/nd/appinit/AppInitFinder", "INITIALIZERS", "Ljava/util/List;")

                for (wc in warehouseClasses) {
                    originalMv.visitFieldInsn(Opcodes.GETSTATIC, "com/nd/appinit/AppInitFinder", "INITIALIZERS", "Ljava/util/List;")
                    originalMv.visitMethodInsn(Opcodes.INVOKESTATIC, wc.replace(".", "/"), "getAllAppInitClass", "()Ljava/util/List;", false)
                    originalMv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "addAll", "(Ljava/util/Collection;)Z", true)
                    originalMv.visitInsn(Opcodes.POP)
                }

                originalMv.visitLabel(initialized)
                originalMv.visitFieldInsn(Opcodes.GETSTATIC, "com/nd/appinit/AppInitFinder", "INITIALIZERS", "Ljava/util/List;")
                originalMv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections", "unmodifiableList", "(Ljava/util/List;)Ljava/util/List;", false)
                originalMv.visitInsn(Opcodes.ARETURN)
                generated = true
                Logger.i("[INJECT] Replacement complete")
            }
        }

        override fun visitInsn(opcode: Int) {
        }

        override fun visitIntInsn(opcode: Int, operand: Int) {
        }

        override fun visitVarInsn(opcode: Int, variable: Int) {
        }

        override fun visitTypeInsn(opcode: Int, type: String) {
        }

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String?) {
        }

        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        }

        override fun visitJumpInsn(opcode: Int, label: Label) {
        }

        override fun visitLabel(label: Label) {
        }

        override fun visitLdcInsn(value: Any?) {
        }

        override fun visitIincInsn(variable: Int, increment: Int) {
        }

        override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any>?, numStack: Int, stack: Array<out Any>?) {
        }

        override fun visitLineNumber(line: Int, start: Label) {
        }

        override fun visitLocalVariable(
            name: String?,
            descriptor: String?,
            signature: String?,
            start: Label?,
            end: Label?,
            index: Int
        ) {
        }

        override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
        }

        override fun visitInvokeDynamicInsn(
            name: String?,
            descriptor: String?,
            bootstrapMethodHandle: Handle?,
            vararg bootstrapMethodArguments: Any?
        ) {
        }

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            originalMv.visitMaxs(0, 0)
        }
    }
}
