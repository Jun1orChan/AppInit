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

        project.extensions.create("appInit", AppInitExtension::class.java)
        Logger.i("[PLUGIN] Created AppInitExtension")

        project.plugins.withId("com.android.application") {
            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
            Logger.i("[PLUGIN] Loaded AndroidComponentsExtension")

            androidComponents.onVariants { variant ->
                val variantName = variant.name
                Logger.i("[PLUGIN] Processing variant: $variantName")
                val extension = project.extensions.getByType(AppInitExtension::class.java)

                if (!extension.enabled) {
                    Logger.i("[PLUGIN] Plugin is disabled, skipping variant: $variantName")
                    return@onVariants
                }

                val taskProvider = project.tasks.register(
                    "${variantName.capitalized()}AppInitTransform",
                    AppInitTransformTask::class.java,
                    androidComponents
                )

                taskProvider.configure { task ->
                    task.verbose.set(project.provider { extension.verbose })
                    task.failOnMissingFinder.set(project.provider { extension.failOnMissingFinder })
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
}
