package com.yura.build

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class VerifyReleaseSigningConfigurationTask : DefaultTask() {
    @get:Input
    abstract val signingConfigured: Property<Boolean>

    @get:Input
    abstract val storeFilePath: Property<String>

    @TaskAction
    fun verify() {
        if (!signingConfigured.get()) {
            throw GradleException(
                "Release signing is incomplete. Configure RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, " +
                    "RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD in ~/.gradle/gradle.properties or CI secrets."
            )
        }

        val configuredStoreFilePath = storeFilePath.get()
        if (!File(configuredStoreFilePath).isFile) {
            throw GradleException("Release keystore does not exist: $configuredStoreFilePath")
        }
    }
}
