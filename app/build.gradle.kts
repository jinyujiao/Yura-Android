import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("plugin.parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

val appVersionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.yura.app"
    compileSdk = (property("android.compileSdk") as String).toInt()

    defaultConfig {
        applicationId = "com.yura.app"
        minSdk = (property("android.minSdk") as String).toInt()
        targetSdk = (property("android.targetSdk") as String).toInt()
        val configuredVersionCode = providers.gradleProperty("APP_VERSION_CODE").orNull?.toIntOrNull()
            ?: appVersionProperties.getProperty("VERSION_CODE")?.toIntOrNull()
            ?: error("VERSION_CODE is missing or invalid in version.properties")
        val configuredVersionName = providers.gradleProperty("APP_VERSION_NAME").orNull
            ?: appVersionProperties.getProperty("VERSION_NAME")
            ?: error("VERSION_NAME is missing in version.properties")
        require(configuredVersionCode > 0) { "VERSION_CODE must be greater than zero" }
        require(configuredVersionName.isNotBlank()) { "VERSION_NAME must not be blank" }
        versionCode = configuredVersionCode
        versionName = configuredVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("localRelease") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
    }
}

val verifyReleaseSigningConfiguration = tasks.register("verifyReleaseSigningConfiguration") {
    group = "verification"
    description = "Fails when a formal release is requested without a complete signing configuration."
    doLast {
        check(releaseSigningConfigured) {
            "Release signing is incomplete. Configure RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, " +
                "RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD in ~/.gradle/gradle.properties or CI secrets."
        }
        check(file(requireNotNull(releaseStoreFile)).isFile) {
            "Release keystore does not exist: $releaseStoreFile"
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseSigningConfiguration)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core)
    implementation(libs.androidx.compose.activity)
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bundles.media3)
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    implementation(project(":tts"))

    implementation(project(":readium:readium-shared"))
    implementation(project(":readium:readium-streamer"))
    implementation(project(":readium:readium-navigator"))
    implementation(project(":readium:navigators:readium-navigator-common"))
    implementation(project(":readium:navigators:web:readium-navigator-web-common"))
    implementation(project(":readium:navigators:web:readium-navigator-web-reflowable"))
    implementation(project(":readium:navigators:web:readium-navigator-web-fixedlayout"))
}
