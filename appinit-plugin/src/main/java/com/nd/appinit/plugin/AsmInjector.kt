package com.nd.appinit.plugin

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
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
        warehouseClasses: List<String>
    ): Map<File, ByteArray?> {
        val modifications = mutableMapOf<File, ByteArray?>()

        // Process all jars
        allJars.forEach { jarFile ->
            val jar = JarFile(jarFile.asFile)
            for (entry in jar.entries()) {
                if (entry.name == "com/nd/appinit/AppInitFinder.class") {
                    val bytes = jar.getInputStream(entry).readBytes()
                    val modified = injectIntoAppInitFinder(bytes, warehouseClasses)
                    if (modified != null) {
                        modifications[jarFile.asFile] = modified
                        Logger.i("[INJECT] Modified AppInitFinder from jar: ${jarFile.asFile.absolutePath}")
                    }
                }
            }
            jar.close()
        }

        // Process directories
        allDirectories.forEach { dir ->
            val finderFile = File(dir.asFile, "com/nd/appinit/AppInitFinder.class")
            if (finderFile.exists()) {
                val bytes = finderFile.readBytes()
                val modified = injectIntoAppInitFinder(bytes, warehouseClasses)
                if (modified != null) {
                    modifications[dir.asFile] = modified
                    finderFile.writeBytes(modified)
                    Logger.i("[INJECT] Modified AppInitFinder in dir: ${dir.asFile}")
                }
            }
        }

        return modifications
    }

    private fun injectIntoAppInitFinder(bytes: ByteArray, warehouseClasses: List<String>): ByteArray? {
        if (warehouseClasses.isEmpty()) {
            return null
        }

        try {
            val reader = ClassReader(bytes)
            val writer = ClassWriter(reader, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

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
                        && !processed
                    ) {
                        processed = true
                        Logger.i("[INJECT] Transforming getAllInitializers with ${warehouseClasses.size} warehouses")

                        val originalMv = cv.visitMethod(access, name, descriptor, signature, exceptions)
                        return InjectMethodVisitor(originalMv, warehouseClasses)
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions)
                }
            }

            reader.accept(visitor, 0)
            return writer.toByteArray()
        } catch (e: Exception) {
            Logger.e("[INJECT] Failed to inject: ${e.message}")
            return null
        }
    }

    /**
     * ASM MethodVisitor that injects warehouse class loading code
     */
    class InjectMethodVisitor(
        private val originalMv: MethodVisitor,
        private val warehouseClasses: List<String>
    ) : MethodVisitor(Opcodes.ASM9, originalMv) {

        private var injected = false

        override fun visitCode() {
            if (!injected) {
                // if (INITIALIZERS == null) { INITIALIZERS = new ArrayList(); }
                visitFieldInsn(Opcodes.GETSTATIC, "com/nd/appinit/AppInitFinder", "INITIALIZERS", "Ljava/util/List;")
                val notNull = Label()
                visitJumpInsn(Opcodes.IFNONNULL, notNull)
                visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
                visitInsn(Opcodes.DUP)
                visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
                visitFieldInsn(Opcodes.PUTSTATIC, "com/nd/appinit/AppInitFinder", "INITIALIZERS", "Ljava/util/List;")

                // INITIALIZERS.addAll(WareHouse.getAllAppInitClass())
                for (wc in warehouseClasses) {
                    visitFieldInsn(Opcodes.GETSTATIC, "com/nd/appinit/AppInitFinder", "INITIALIZERS", "Ljava/util/List;")
                    visitMethodInsn(Opcodes.INVOKESTATIC, wc.replace(".", "/"), "getAllAppInitClass", "()Ljava/util/List;", false)
                    visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "addAll", "(Ljava/util/Collection;)Z", true)
                    visitInsn(Opcodes.POP)
                }

                visitLabel(notNull)
                injected = true
                Logger.i("[INJECT] Injection complete")
            }
            originalMv.visitCode()
        }

        override fun visitFieldInsn(op: Int, owner: String, name: String, desc: String?) =
            originalMv.visitFieldInsn(op, owner, name, desc)

        override fun visitMethodInsn(op: Int, owner: String, name: String, desc: String, iface: Boolean) =
            originalMv.visitMethodInsn(op, owner, name, desc, iface)

        override fun visitInsn(op: Int) = originalMv.visitInsn(op)
        override fun visitLabel(label: Label) = originalMv.visitLabel(label)
        override fun visitJumpInsn(op: Int, label: Label) = originalMv.visitJumpInsn(op, label)
        override fun visitTypeInsn(op: Int, type: String) = originalMv.visitTypeInsn(op, type)
        override fun visitMaxs(maxStack: Int, maxLocals: Int) = originalMv.visitMaxs(Math.max(maxStack, 4), maxLocals)
        override fun visitEnd() { originalMv.visitEnd(); super.visitEnd() }
    }
}