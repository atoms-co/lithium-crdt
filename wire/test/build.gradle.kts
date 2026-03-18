plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.wire.runtime)
    implementation(projects.wireData)
    implementation(projects.fixtures)
}

// Configure Wire proto compilation for test messages
val generateWireTestProtos by tasks.registering(JavaExec::class) {
    description = "Generate Wire protobuf test classes"
    group = "build"

    val protoSourceDir = file("src/main/proto")
    val dataProtoDir = project(":data").file("src/main/proto")
    val outputDir = layout.buildDirectory.dir("generated/source/wire").get().asFile

    inputs.dir(protoSourceDir)
    inputs.dir(dataProtoDir)
    outputs.dir(outputDir)

    classpath = configurations.detachedConfiguration(
        dependencies.create(libs.wire.compiler.get())
    )
    mainClass.set("com.squareup.wire.WireCompiler")

    args(
        "--proto_path=${protoSourceDir.absolutePath}",
        "--proto_path=${dataProtoDir.absolutePath}",
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
    dependsOn(generateWireTestProtos)
}

tasks.named("sourcesJar") {
    dependsOn(generateWireTestProtos)
}

kotlin {
    sourceSets.getByName("main").kotlin.srcDir(layout.buildDirectory.dir("generated/source/wire"))
}

// Note: This module is not published - it's only for testing wire module
