plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.wire) apply false
}

// Shared configuration for all projects
allprojects {
    group = "com.css.internal.shared.storage.crdt"
    version = project.findProperty("version")?.toString() ?: "1.0.0-SNAPSHOT"

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

    // Configure Java plugin if applied
    plugins.withId("java") {
        configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    // Configure Java Library plugin if applied
    plugins.withId("java-library") {
        configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
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
        // Load settings for publishing
        val buildWithoutVpn = try {
            File(rootProject.projectDir, "local.properties").reader().use {
                val properties = java.util.Properties()
                properties.load(it)
                properties.getProperty("css.buildWithoutVpn")?.toBoolean() ?: false
            }
        } catch (_: Exception) {
            false
        }

        val repoHost = if (buildWithoutVpn) {
            rootProject.extra["css.repo.hostname.no.vpn"] as? String
        } else {
            rootProject.extra["css.repo.hostname.use.vpn"] as? String
        }

        val accessToken = if (buildWithoutVpn && repoHost != null) {
            val userHome = System.getProperty("user.home")
            val netrcFile = File(userHome, ".netrc")
            if (netrcFile.exists()) {
                val results = HashMap<String, MutableMap<String, String>>()
                netrcFile.useLines { lines ->
                    var currentMachine: String? = null
                    var entry = mutableMapOf<String, String>()
                    for (lineIn in lines) {
                        val line = lineIn.trim()
                        if (line.isEmpty() || line.startsWith("#")) continue
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            val key = parts[0].lowercase()
                            val value = parts[1]
                            if (key == "machine") {
                                if (currentMachine != null) {
                                    results[currentMachine] = entry
                                    entry = mutableMapOf()
                                }
                                currentMachine = value
                            } else {
                                entry[key] = value
                            }
                        }
                    }
                    if (currentMachine != null) {
                        results[currentMachine] = entry
                    }
                }
                results[repoHost]?.get("password")
            } else null
        } else null

        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "Artifactory"
                    url = uri("https://$repoHost/artifactory/monorepo-local")

                    if (buildWithoutVpn && accessToken != null) {
                        authentication {
                            create<HttpHeaderAuthentication>("header")
                        }
                        credentials(HttpHeaderCredentials::class) {
                            this.name = "Authorization"
                            value = "Bearer $accessToken"
                        }
                    }
                }
            }
        }
    }
}
