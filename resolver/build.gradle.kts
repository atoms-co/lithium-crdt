plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "crdt-resolver"
            pom {
                name.set("CRDT Resolver")
                description.set("Core CRDT conflict resolution algorithms with zero external dependencies")
            }
        }
    }
}
