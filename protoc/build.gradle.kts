plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

dependencies {
    // Implementation dependencies
    implementation(libs.protobuf.java)

    // Project dependencies
    implementation(project(":protoc-data"))
    implementation(project(":resolver"))

    // Test dependencies
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.junit.jupiter)

    testImplementation(project(":fixtures"))
    testImplementation(project(":protoc-test"))

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "crdt-protoc"
            pom {
                name.set("CRDT Protoc")
                description.set("CRDT conflict resolution for Google protobuf (Java/backend)")
            }
        }
    }
}
