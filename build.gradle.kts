import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit
import io.izzel.taboolib.gradle.CommandHelper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val publishUsername: String by project
val publishPassword: String by project
val build: String by project

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.1.20"
    id("io.izzel.taboolib") version "2.0.23"
}

taboolib {
    env {
        install(Basic)
        install(Bukkit)
        install(CommandHelper)
        // repoTabooLib = "https://nexus.mcwar.cn/repository/maven-public/"
    }
    description {
        name = "RedisChannel"
        contributors {
            name("zhibei")
        }
    }
    version { taboolib = "6.2.4-abd325ee" }
    relocate("org.reactivestreams", "com.gitee.redischannel.reactivestreams")
    relocate("reactor", "com.gitee.redischannel.reactor")
    relocate("org.apache.commons.pool2", "com.gitee.redischannel.commons.pool2")
    relocate("io.netty", "com.gitee.redischannel.netty")
}

repositories {
    mavenCentral()
    maven("https://jfrog.mcwar.cn/artifactory/maven-releases")
}

dependencies {
    compileOnly("ink.ptms.core:v12004:12004:mapped")
    compileOnly("ink.ptms.core:v12004:12004:universal")

    api("io.lettuce:lettuce-core:7.2.1.RELEASE")
    compileOnly("org.apache.commons:commons-pool2:2.12.0")

    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.set(listOf("-Xjvm-default=all"))
    }
}

tasks.withType<Jar> {
    destinationDirectory.set(File(build))
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

publishing {
    repositories {
        maven {
            url = uri("https://jfrog.mcwar.cn/artifactory/maven-releases")
            credentials {
                username = publishUsername
                password = publishPassword
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifact(tasks["kotlinSourcesJar"]) {
                classifier = "sources"
            }
            artifact("${build}/${rootProject.name}-${version}-api.jar") {
                classifier = "api"
            }
            groupId = project.group.toString()
        }
    }
}