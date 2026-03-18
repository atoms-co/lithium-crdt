import java.util.Properties
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.wire) apply false
    alias(libs.plugins.central.publishing) apply false
}

version = determineVersion()

// Shared configuration for all projects
allprojects {
    group = "co.atoms.lithium.crdt"
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
        // Add sources/javadoc JARs by default for all Kotlin modules
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

    // When vanniktech is applied alongside kotlin.jvm, remove the Java-plugin
    // javadoc jar to avoid duplicate 'jar.asc'/'javadoc' artifacts
    // (vanniktech provides its own via mavenPlainJavadocJar).
    plugins.withId("com.vanniktech.maven.publish") {
        plugins.withId("org.jetbrains.kotlin.jvm") {
            tasks.named("javadocJar") { enabled = false }
        }
    }

    // Configure Maven Central publishing if vanniktech plugin is applied
    plugins.withId("com.vanniktech.maven.publish") {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            signAllPublications()

            pom {
                url.set("https://github.com/atoms-co/lithium-crdt")
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
                    connection.set("scm:git:git://github.com/atoms-co/lithium-crdt.git")
                    developerConnection.set("scm:git:ssh://github.com:atoms-co/lithium-crdt.git")
                    url.set("https://github.com/atoms-co/lithium-crdt")
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
