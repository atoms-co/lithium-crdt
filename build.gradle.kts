import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.wire) apply false
}

version = determineVersion()

// Shared configuration for all projects
allprojects {
    group = "com.css.protobuf.crdt"
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
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "artifactory"
                    url = project.uri(
                        findPropertyFromProject(
                            project,
                            "artifactoryUrl",
                            "https://artifactory.cssvpn.com/artifactory/monorepo-local"
                        )
                    )
                    credentials {
                        username = findPropertyFromProject(project, "artifactoryUsername")
                        password = findPropertyFromProject(project, "artifactoryPassword")
                    }
                }
            }
        }
    }
}

tasks {
    withType<PublishToMavenRepository>().configureEach {
        doLast("reportUploadFor${name.replaceFirstChar { it.uppercaseChar() }}") {
            val landerCiOutputDirectory = File("/home/executor/.archive/")
            val landerCiUploadLocationFile = File(landerCiOutputDirectory, "upload.txt")
            val repoUrl = getExternallyAccessibleCSSUrl(repository.url)
            val groupPath = publication.groupId.replace('.', '/')
            val artifactId = publication.artifactId
            val version = publication.version
            val baseUrl = "$repoUrl/$groupPath/$artifactId/$version/"

            publication.artifacts.forEach { artifact ->
                val artifactName = "$artifactId-$version"
                val uploadLocation = "${baseUrl}$artifactName.${artifact.extension}"
                project.logger.lifecycle("Library uploaded to: $uploadLocation")

                // Create the CI upload location file if it doesn't exist and then append to it.
                landerCiUploadLocationFile.createNewFile()
                landerCiUploadLocationFile.appendText(uploadLocation + "\n")
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
    // artifactoryUsername -> ARTIFACTORY_USERNAME
    val envVarName = key.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
    return System.getenv(envVarName)
        ?: System.getProperty(key)
        ?: project.rootProject.properties.getOrDefault(key, defaultVal).toString()
}

fun getExternallyAccessibleCSSUrl(repoUrl: URI): String {
    return when {
        // Replace all private vpn based url with externally accessible hosts
        repoUrl.toString().contains(".cssvpn") -> repoUrl.toString().replace(
            oldValue = ".cssvpn",
            newValue = ".cssinternal"
        )
        else -> repoUrl.toString()
    }
}
