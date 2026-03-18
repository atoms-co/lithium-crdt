plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.central.publishing)
}

dependencies {
    // Implementation dependencies
    implementation(libs.kotlin.coroutines)
    implementation(libs.measured)
    implementation(libs.wire.runtime)

    // Project dependencies
    implementation(projects.wireData)
    implementation(projects.resolver)

    // Test dependencies
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.junit.jupiter)

    testImplementation(projects.fixtures)
    testImplementation(projects.wireTest)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

mavenPublishing {
    coordinates(artifactId = "crdt-wire")
    pom {
        name.set("CRDT Wire")
        description.set("CRDT conflict resolution for Wire protobuf library (Kotlin/Android)")
    }
}
