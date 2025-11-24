plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

dependencies {
    // Implementation dependencies
    implementation(libs.kotlin.coroutines)
    implementation(libs.measured)
    implementation(libs.wire.runtime)

    // Project dependencies
    implementation(project(":data"))
    implementation(project(":resolver"))

    // Test dependencies
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.junit.jupiter)

    testImplementation(project(":fixtures"))
    testImplementation(project(":wire-test"))

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "crdt-wire"
            pom {
                name.set("CRDT Wire")
                description.set("CRDT conflict resolution for Wire protobuf library (Kotlin/Android)")
            }
        }
    }
}
