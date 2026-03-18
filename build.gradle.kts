import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.wire) apply false
    signing
}

version = determineVersion()

// Shared configuration for all projects
allprojects {
    group = "co.atoms.protobuf.crdt"
    version = rootProject.version

    // Ensure consistent Java version across all modules
    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}

// Shared configuration for subprojects
subprojects {
    // Configure testing for all modules
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }

    // Configure Kotlin JVM plugin if applied
    plugins.withId("org.jetbrains.kotlin.jvm") {
        configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
        }

        // Configure sourcesJar to handle duplicates
        tasks.withType<Jar>().configureEach {
            if (name == "sourcesJar") {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        }
    }

    // Configure Maven publishing if applied
    plugins.withId("maven-publish") {
        apply(plugin = "signing")

        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "mavenCentral"
                    url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = findPropertyFromProject(project, "mavenCentralUsername")
                        password = findPropertyFromProject(project, "mavenCentralPassword")
                    }
                }
            }
        }

        configure<SigningExtension> {
            val signingKey = findPropertyFromProject(project, "gpgSigningKey")
            val signingPassword = findPropertyFromProject(project, "gpgSigningKeyPassword")
            if (signingKey.isNotBlank()) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(the<PublishingExtension>().publications)
            }
        }

        // Shared POM metadata for all published modules
        configure<PublishingExtension> {
            publications.withType<MavenPublication> {
                pom {
                    url.set("https://github.com/atoms-co/protobuf-crdt")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("atoms")
                            name.set("Atoms")
                            url.set("https://atoms.co")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/atoms-co/protobuf-crdt.git")
                        developerConnection.set("scm:git:ssh://github.com:atoms-co/protobuf-crdt.git")
                        url.set("https://github.com/atoms-co/protobuf-crdt")
                    }
                }
            }
        }
    }
}

fun determineVersion(): String {
    val props = Properties()
    project.file("gradle.properties").reader().use {
        props.load(it)
    }

    val majorVersion = props.getProperty("version.major")
    val minorVersion = props.getProperty("version.minor")
    val patchVersion = props.getProperty("version.patch")
    return "$majorVersion.$minorVersion.$patchVersion"
}

fun findPropertyFromProject(project: Project, key: String, defaultVal: String = ""): String {
    // Convert camelCase to SCREAMING_SNAKE_CASE for environment variables
    // mavenCentralUsername -> MAVEN_CENTRAL_USERNAME
    val envVarName = key.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
    return System.getenv(envVarName)
        ?: System.getProperty(key)
        ?: project.rootProject.properties.getOrDefault(key, defaultVal).toString()
}
