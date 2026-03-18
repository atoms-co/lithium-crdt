plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.central.publishing)
}

dependencies {
    implementation(libs.protobuf.java)

    testImplementation(libs.truth)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }

    sourceSets {
        main {
            proto {
                // Source proto files from data module
                srcDir(project(":data").file("src/main/proto"))
            }
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                java {}
            }
        }
    }
}

mavenPublishing {
    coordinates(artifactId = "crdt-protoc-data")
    pom {
        name.set("CRDT Protoc Data")
        description.set("Protocol Buffer data structures for CRDT version management (protoc-generated)")
    }
}
