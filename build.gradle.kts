plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.compose.compiler) apply false
    id("org.jetbrains.dokka")
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    ktlint {
        android.set(true)
        version = "1.8.0"
    }

    afterEvaluate {
        if (tasks.findByName("clean") == null) {
            tasks.register<Delete>("clean") {
                delete(layout.buildDirectory)
            }
        }
    }
}

tasks.register("cleanDocs", Delete::class).configure {
    delete(
        "${project.rootDir}/docs/api",
        "${project.rootDir}/docs/index.md",
        "${project.rootDir}/site"
    )
}

dokka {
    dokkaSourceSets.configureEach {
        reportUndocumented.set(false)
        skipEmptyPackages.set(false)
        skipDeprecated.set(true)
    }
}

dependencies {
    subprojects
        .filter { it.name != "app" }
        .filter { it.buildFile.exists() }
        .forEach { dokka(project(it.path)) }
}
