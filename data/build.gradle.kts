plugins {
    base
    `maven-publish`
    alias(libs.plugins.central.publishing)
}

// Create a task to package proto files into a JAR
val protoJar by tasks.registering(Jar::class) {
    archiveBaseName.set("crdt-data")
    from(projectDir) {
        include("src/main/proto/**/*.proto")
        eachFile {
            // Remove the src/main/proto prefix to place protos at root
            relativePath = RelativePath(true, *relativePath.segments.drop(3).toTypedArray())
        }
        includeEmptyDirs = false
    }
}

// Make the build task depend on protoJar
tasks.named("build") {
    dependsOn(protoJar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(protoJar)
            artifactId = "crdt-data"
            pom {
                name.set("CRDT Data")
                description.set("Protocol Buffer schema definitions for CRDT version management")
            }
        }
    }
}
