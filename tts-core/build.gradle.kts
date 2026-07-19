plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
