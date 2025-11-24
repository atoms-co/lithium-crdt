@file:Suppress("UnstableApiUsage")

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "protobuf-crdt"

// Helper function for .netrc parsing (used in multiple scopes)
fun getAccessTokenForHost(host: String?): String? {
    if (host == null) return null
    val userHome = System.getProperty("user.home")
    val netrcFile = File(userHome, ".netrc")
    if (!netrcFile.exists()) return null

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

    return results[host]?.get("password")
}

// Plugin management - needs own variable definitions due to scope
pluginManagement {
    // Helper function for .netrc parsing (local to pluginManagement scope)
    fun getAccessTokenForHost(host: String?): String? {
        if (host == null) return null
        val userHome = System.getProperty("user.home")
        val netrcFile = File(userHome, ".netrc")
        if (!netrcFile.exists()) return null

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

        return results[host]?.get("password")
    }

    // Load local.properties settings inside pluginManagement scope
    val buildWithoutVpn = try {
        File(rootProject.projectDir, "local.properties").reader().use {
            val properties = java.util.Properties()
            properties.load(it)
            properties.getProperty("css.buildWithoutVpn")?.toBoolean() ?: false
        }
    } catch (_: Exception) {
        false
    }

    val preferExternalRepositories = try {
        File(rootProject.projectDir, "local.properties").reader().use {
            val properties = java.util.Properties()
            properties.load(it)
            properties.getProperty("css.preferExternalRepositories")?.toBoolean() ?: false
        }
    } catch (_: Exception) {
        false
    }

    val repoHost = if (buildWithoutVpn) {
        extra["css.repo.hostname.no.vpn"] as? String
    } else {
        extra["css.repo.hostname.use.vpn"] as? String
    }

    val accessToken = if (buildWithoutVpn) getAccessTokenForHost(repoHost) else null

    repositories {
        if (preferExternalRepositories) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            if (repoHost != null) {
                maven {
                    setUrl("https://$repoHost/artifactory/plugin-portal")
                    if (buildWithoutVpn && accessToken != null) {
                        authentication {
                            create<HttpHeaderAuthentication>("header")
                        }
                        credentials(HttpHeaderCredentials::class) {
                            name = "Authorization"
                            value = "Bearer $accessToken"
                        }
                    }
                }
                maven {
                    setUrl("https://$repoHost/artifactory/gradle-plugins-local")
                    if (buildWithoutVpn && accessToken != null) {
                        authentication {
                            create<HttpHeaderAuthentication>("header")
                        }
                        credentials(HttpHeaderCredentials::class) {
                            name = "Authorization"
                            value = "Bearer $accessToken"
                        }
                    }
                }
            }

            // Always include public repositories as fallback
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

// Load settings for dependency resolution (outer scope)
val buildWithoutVpn = try {
    File(rootProject.projectDir, "local.properties").reader().use {
        val properties = java.util.Properties()
        properties.load(it)
        properties.getProperty("css.buildWithoutVpn")?.toBoolean() ?: false
    }
} catch (_: Exception) {
    false
}

val preferExternalRepositories = try {
    File(rootProject.projectDir, "local.properties").reader().use {
        val properties = java.util.Properties()
        properties.load(it)
        properties.getProperty("css.preferExternalRepositories")?.toBoolean() ?: false
    }
} catch (_: Exception) {
    false
}

val repoHost: String? = if (buildWithoutVpn) {
    extra["css.repo.hostname.no.vpn"] as? String
} else {
    extra["css.repo.hostname.use.vpn"] as? String
}

val accessToken: String? = if (buildWithoutVpn) getAccessTokenForHost(repoHost) else null

// Helper function for Artifactory authentication
fun MavenArtifactRepository.artifactoryAuth(url: String) {
    setUrl(url)
    if (buildWithoutVpn && accessToken != null) {
        authentication {
            create<HttpHeaderAuthentication>("header")
        }
        credentials(HttpHeaderCredentials::class) {
            name = "Authorization"
            value = "Bearer $accessToken"
        }
    }
}

// Dependency resolution management
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        if (preferExternalRepositories) {
            google()
            mavenCentral()
            if (repoHost != null) {
                maven { setUrl("https://$repoHost/artifactory/maven-release") }
            }
        } else {
            if (repoHost != null) {
                maven { artifactoryAuth("https://$repoHost/artifactory/monorepo-local") }
                maven { artifactoryAuth("https://$repoHost/artifactory/maven-release") }
            }

            // Always include public repositories as fallback
            google()
            mavenCentral()
        }
    }

    // Version catalogs are automatically loaded from gradle/libs.versions.toml
}

// Include all modules
include(":resolver")
include(":data")
include(":wire")
include(":protoc")
include(":protoc-data")
include(":fixtures")
include(":wire-test")
include(":protoc-test")

// Map test modules to their directory structure
project(":wire-test").projectDir = file("wire/test")
project(":protoc-test").projectDir = file("protoc/test")

// VPN check (optional - only if configured)
if (!buildWithoutVpn && repoHost != null) {
    gradle.taskGraph.whenReady {
        val airplaneMode = try {
            File(rootProject.projectDir, "local.properties").reader().use {
                val properties = java.util.Properties()
                properties.load(it)
                properties.getProperty("css.airplaneMode")?.toBoolean() ?: false
            }
        } catch (_: Exception) {
            false
        }

        if (!airplaneMode) {
            try {
                val client = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("https://$repoHost/"))
                    .build()

                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply { it.statusCode() }
                    .thenAccept { println("✓ Artifactory connection verified") }
                    .join()
            } catch (e: Exception) {
                logger.warn("Warning: Could not connect to Artifactory at $repoHost")
                logger.warn("Add 'css.airplaneMode=true' to local.properties to disable this check")
            }
        }
    }
}
