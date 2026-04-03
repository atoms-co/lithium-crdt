plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    application
}

val ktorVersion = "3.1.3"
val serializationVersion = "1.8.1"

dependencies {
    // Wire runtime + CRDT library
    implementation(libs.wire.runtime)
    implementation(projects.wireData)
    implementation(projects.wire)
    implementation(projects.resolver)

    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    // Coroutines
    implementation(libs.kotlin.coroutines)
}

application {
    mainClass.set("co.atoms.lithium.crdt.examples.MainKt")
}

// Configure Wire proto compilation for example messages
val generateWireExampleProtos by tasks.registering(JavaExec::class) {
    description = "Generate Wire protobuf classes for example messages"
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
    dependsOn(generateWireExampleProtos)
}

tasks.named("sourcesJar") {
    dependsOn(generateWireExampleProtos)
}

kotlin {
    sourceSets.getByName("main").kotlin.srcDir(layout.buildDirectory.dir("generated/source/wire"))
}

// Note: This module is not published - it's an example/demo application
