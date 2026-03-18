plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.wire)
    `maven-publish`
}

wire {
    protoLibrary = true

    // Source proto files from the data module
    sourcePath {
        srcDir(project(":data").file("src/main/proto"))
    }

    kotlin {
        buildersOnly = false
        javaInterop = false
        emitDeclaredOptions = true
        emitAppliedOptions = true
    }
}

dependencies {
    implementation(libs.wire.runtime)

    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "crdt-wire-data"
            pom {
                name.set("CRDT Wire Data")
                description.set("Protocol Buffer data structures for CRDT version management (Wire-generated)")
            }
        }
    }
}
