package me.tatarka.inject.test

import assertk.Assert
import assertk.assertions.isInstanceOf
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import java.io.File

class ProjectCompiler(private val root: File, private val target: Target) {

    private val sources = mutableListOf<Pair<String, String>>()

    fun source(fileName: String, source: String): ProjectCompiler {
        sources.add(fileName to source)
        return this
    }

    fun compile() {
        val kotlinVersion = if (target == Target.ksp) "1.4-M1" else "1.3.70"

        val settingsFile = root.resolve("settings.gradle")

        val pluginManagement = """
           pluginManagement {
               repositories {
                   google()
                   maven { url "https://dl.bintray.com/kotlin/kotlin-eap" }
                   mavenCentral()
                   gradlePluginPortal()
               }
               resolutionStrategy {
                   eachPlugin {
                       switch (requested.id.id) {
                           case "kotlin-ksp":
                           case "org.jetbrains.kotlin.kotlin-ksp":
                           case "org.jetbrains.kotlin.ksp":
                               useModule("org.jetbrains.kotlin:kotlin-ksp:" + requested.version)
                       }
                   }
               }
           }
        """.trimIndent()

        settingsFile.writeText(
            """
            ${if (target == Target.ksp) pluginManagement else ""}
           
           include ':kotlin-inject-compiler-core'
           include ':kotlin-inject-compiler-${target}'
           include ':kotlin-inject-runtime'
           include ':compiler-test'
           project(':kotlin-inject-compiler-core').projectDir = new File('${File("../kotlin-inject-compiler-core").absolutePath}')
           project(':kotlin-inject-compiler-${target}').projectDir = new File('${File("../kotlin-inject-compiler-${target}").absolutePath}')
           project(':kotlin-inject-runtime').projectDir = new File('${File("../kotlin-inject-runtime").absolutePath}')
        """.trimIndent()
        )

        val rootBuildFile = root.resolve("build.gradle")

        rootBuildFile.writeText(
            """
            allprojects {
                repositories {
                    google()
                    maven { url "https://dl.bintray.com/kotlin/kotlin-eap" }
                    mavenCentral()
                }
            }
            """.trimIndent()
        )

        val dir = root.resolve("compiler-test")
        dir.mkdirs()

        val buildFile = dir.resolve("build.gradle")

        val pluginVersion = if (target == Target.ksp) "1.4-M1-dev-experimental-20200610" else kotlinVersion

        buildFile.writeText(
            """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '${kotlinVersion}'
                id("org.jetbrains.kotlin.$target") version '${pluginVersion}'
            }
            
            dependencies {
                implementation 'org.jetbrains.kotlin:kotlin-stdlib'
                $target project(':kotlin-inject-compiler-${target}')
                implementation project(':kotlin-inject-runtime')
            }
            """.trimIndent()
        )

        val sourceDir = dir.resolve("src/main/kotlin")
        sourceDir.mkdirs()

        for ((file, source) in sources) {
            val sourceFile = sourceDir.resolve(file)
            sourceFile.writeText(source)
        }

        GradleRunner.create()
            .withProjectDir(root)
            .withArguments("assemble")
            .build()
    }
}

fun Assert<Throwable>.output() = isInstanceOf(UnexpectedBuildFailure::class).transform { actual ->
    actual.buildResult.output
}

enum class Target {
    kapt, ksp
}
