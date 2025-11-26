plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
    `maven-publish`
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
                // Source proto files from wire-data module
                srcDir(project(":wire-data").file("src/main/proto"))
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "crdt-protoc-data"
            pom {
                name.set("CRDT Protoc Data")
                description.set("Protocol Buffer data structures for CRDT version management (protoc-generated)")
            }
        }
    }
}
