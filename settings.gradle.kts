@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "protobuf-crdt"

// Plugin management
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Dependency resolution management
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        google()
        mavenCentral()
    }

    // Version catalogs are automatically loaded from gradle/libs.versions.toml
}

// Include all modules
include(":data")
include(":resolver")
include(":wire-data")
include(":wire")
include(":protoc")
include(":protoc-data")
include(":fixtures")
include(":wire-test")
include(":protoc-test")

// Map test modules to their directory structure
project(":wire-test").projectDir = file("wire/test")
project(":protoc-test").projectDir = file("protoc/test")
