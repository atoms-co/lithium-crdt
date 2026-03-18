plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.central.publishing)
}

dependencies {
    // No implementation dependencies - pure Kotlin stdlib

    // Test dependencies
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

mavenPublishing {
    coordinates(artifactId = "crdt-resolver")
    pom {
        name.set("CRDT Resolver")
        description.set("Core CRDT conflict resolution algorithms with zero external dependencies")
    }
}
