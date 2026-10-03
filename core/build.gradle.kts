// Pure Kotlin/JVM module: all metric maths lives here so it is fast to unit test
// and has zero Android or network dependencies.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
