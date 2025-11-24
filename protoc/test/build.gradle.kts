plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

// Handle duplicate proto files during resource processing
tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                java {
                    // Generate test protos for validation
                }
            }
        }
    }

    sourceSets {
        main {
            proto {
                // Reference proto files from wire-test module
                srcDir(project(":wire-test").file("src/main/proto"))
                // Also include data module protos
                srcDir(project(":data").file("src/main/proto"))
            }
        }
    }
}

dependencies {
    implementation(project(":protoc-data"))
    implementation(libs.protobuf.java)
    implementation(project(":fixtures"))

    testImplementation(libs.mockk)
    testImplementation(libs.truth)
}

// Note: This module is not published - it's only for testing protoc module
