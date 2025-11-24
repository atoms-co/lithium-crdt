plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

dependencies {
    implementation(libs.wire.runtime)

    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Configure Wire proto compilation
val generateWireProtos by tasks.registering(JavaExec::class) {
    description = "Generate Wire protobuf classes"
    group = "build"

    val protoSourceDir = file("src/main/proto")
    val outputDir = layout.buildDirectory.dir("generated/source/wire").get().asFile

    inputs.dir(protoSourceDir)
    outputs.dir(outputDir)

    classpath = configurations.detachedConfiguration(
        dependencies.create(libs.wire.compiler.get())
    )
    mainClass.set("com.squareup.wire.WireCompiler")

    args(
        "--proto_path=${protoSourceDir.absolutePath}",
        "--kotlin_out=${outputDir.absolutePath}",
        *protoSourceDir.walk()
            .filter { it.extension == "proto" }
            .map { it.relativeTo(protoSourceDir).path }
            .toList()
            .toTypedArray()
    )

    doFirst {
        outputDir.mkdirs()
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateWireProtos)
}

tasks.named("sourcesJar") {
    dependsOn(generateWireProtos)
}

kotlin {
    sourceSets.getByName("main").kotlin.srcDir(layout.buildDirectory.dir("generated/source/wire"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "crdt-data"
            pom {
                name.set("CRDT Data")
                description.set("Protocol Buffer data structures for CRDT version management (Wire-generated)")
            }
        }
    }
}
