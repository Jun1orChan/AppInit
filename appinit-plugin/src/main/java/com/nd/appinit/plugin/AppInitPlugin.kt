package com.nd.appinit.plugin

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized

/**
 * AppInit Gradle plugin.
 * Uses project.tasks.register + variant.artifacts.forScope().toTransform() approach
 * similar to DRouter for full control over scan and inject execution order.
 */
class AppInitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        Logger.i("[PLUGIN] Applying plugin to: ${project.name}")

        if (project.name != "app") {
            Logger.i("[PLUGIN] Skipping non-app project: ${project.name}")
            return
        }

        project.extensions.create("appInit", AppInitExtension::class.java)
        Logger.i("[PLUGIN] Created AppInitExtension")

        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        Logger.i("[PLUGIN] Loaded AndroidComponentsExtension")

        androidComponents.onVariants { variant ->
            val variantName = variant.name
            Logger.i("[PLUGIN] Processing variant: $variantName")

            if (!project.extensions.getByType(AppInitExtension::class.java).enabled) {
                Logger.i("[PLUGIN] Plugin is disabled, skipping variant: $variantName")
                return@onVariants
            }

            val taskProvider = project.tasks.register(
                "${variantName.capitalized()}AppInitTransform",
                AppInitTransformTask::class.java,
                androidComponents
            )

            taskProvider.configure { _ ->
                // No additional configuration needed - all data comes from input artifacts
            }

            @Suppress("UnstableApiUsage")
            val transformParams = ScopedArtifacts.Scope.ALL

            variant.artifacts.forScope(transformParams).use(taskProvider)
                .toTransform(
                    ScopedArtifact.CLASSES,
                    AppInitTransformTask::allJars,
                    AppInitTransformTask::allDirectories,
                    AppInitTransformTask::output
                )
            Logger.i("[PLUGIN] Registered AppInitTransformTask")
        }
    }
}