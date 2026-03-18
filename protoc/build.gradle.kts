plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.central.publishing)
}

dependencies {
    // Implementation dependencies
    implementation(libs.protobuf.java)

    // Project dependencies
    implementation(projects.protocData)
    implementation(projects.resolver)

    // Test dependencies
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.junit.jupiter)

    testImplementation(projects.fixtures)
    testImplementation(projects.protocTest)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

mavenPublishing {
    coordinates(artifactId = "crdt-protoc")
    pom {
        name.set("CRDT Protoc")
        description.set("CRDT conflict resolution for Google protobuf (Java/backend)")
    }
}
