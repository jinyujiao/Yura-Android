plugins {
    id("com.android.library")
}

android {
    namespace = "com.yura.tts"
    compileSdk = (property("android.compileSdk") as String).toInt()

    defaultConfig {
        minSdk = (property("android.minSdk") as String).toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(project(":tts-core"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bundles.media3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
