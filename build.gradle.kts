import io.izzel.taboolib.gradle.*
import java.util.jar.JarFile

val publishUsername = providers.gradleProperty("publishUsername").orNull ?: ""
val publishPassword = providers.gradleProperty("publishPassword").orNull ?: ""
val buildOutput = providers.gradleProperty("build").orElse("build").get()

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.1.20"
    id("io.izzel.taboolib") version "2.0.37"
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
    version {
        taboolib = "6.3.0-932e79c"
        coroutines = "1.10.1"
    }
    relocate("org.reactivestreams", "com.gitee.redischannel.reactivestreams")
    relocate("reactor", "com.gitee.redischannel.reactor")
    relocate("io.netty", "com.gitee.redischannel.netty")
    relocate("org.slf4j", "com.gitee.redischannel.slf4j")
    relocate("redis.clients.authentication", "com.gitee.redischannel.redis.clients.authentication")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("ink.ptms.core:v11200:11200")

    api("io.lettuce:lettuce-core:6.8.0.RELEASE")

    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:4.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjdk-release=8")
    }
}

tasks.withType<Jar> {
    destinationDirectory.set(File(buildOutput))
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(17)
}

val stableApiJar = tasks.register<Jar>("stableApiJar") {
    dependsOn(tasks.classes)
    archiveClassifier.set("api")
    destinationDirectory.set(file(buildOutput))
    from(sourceSets.main.get().output) {
        include("com/gitee/redischannel/RedisChannelPlugin.class")
        include("com/gitee/redischannel/RedisChannelPlugin\$*.class")
        include("com/gitee/redischannel/api/**/*.class")
    }
}

val verifyApiArtifact = tasks.register("verifyApiArtifact") {
    dependsOn(stableApiJar)
    doLast {
        val apiJar = file("$buildOutput/${rootProject.name}-${project.version}-api.jar")
        check(apiJar.isFile) { "API artifact not found: ${apiJar.absolutePath}" }
        val publicApiClasses = setOf(
            "com/gitee/redischannel/api/RedisCommandAPI.class",
            "com/gitee/redischannel/api/RedisPubSubAPI.class",
            "com/gitee/redischannel/api/cluster/RedisClusterCommandAPI.class",
            "com/gitee/redischannel/api/cluster/RedisClusterPubSubAPI.class"
        )
        var projectClassCount = 0
        JarFile(apiJar).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("com/gitee/redischannel/") && it.name.endsWith(".class") }
                .forEach { entry ->
                    check(
                        entry.name == "com/gitee/redischannel/RedisChannelPlugin.class" ||
                            entry.name.startsWith("com/gitee/redischannel/RedisChannelPlugin\$") ||
                            entry.name.startsWith("com/gitee/redischannel/api/")
                    ) { "Implementation class leaked into API artifact: ${entry.name}" }
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }
                    check(bytes.size >= 8) { "Invalid class file: ${entry.name}" }
                    val major = ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
                    check(major <= 52) { "${entry.name} targets class version $major instead of Java 8" }
                    if (entry.name in publicApiClasses) {
                        val constants = String(bytes, Charsets.ISO_8859_1)
                        check("executeReactive" !in constants) { "Reactive method leaked into ${entry.name}" }
                        check("org/reactivestreams" !in constants) { "Reactive Streams type leaked into ${entry.name}" }
                        check("reactor/core" !in constants) { "Reactor type leaked into ${entry.name}" }
                    }
                    projectClassCount++
                }
        }
        check(projectClassCount > 0) { "No RedisChannel classes found in API artifact" }
    }
}

val verifyApiConsumer = tasks.register("verifyApiConsumer") {
    dependsOn(verifyApiArtifact)
    doLast {
        val apiJar = file("$buildOutput/${rootProject.name}-${project.version}-api.jar")
        val sourceDir = layout.buildDirectory.dir("tmp/api-consumer").get().asFile
        val outputDir = File(sourceDir, "classes")
        sourceDir.mkdirs()
        outputDir.mkdirs()
        val sourceFile = File(sourceDir, "ApiConsumer.java")
        sourceFile.writeText(
            """
            import com.gitee.redischannel.RedisChannelPlugin;
            import com.gitee.redischannel.api.RedisChannelAPI;
            import com.gitee.redischannel.api.RedisCommandAPI;
            import com.gitee.redischannel.api.RedisPubSubAPI;
            import com.gitee.redischannel.api.RedisLifecycleSnapshot;
            import com.gitee.redischannel.api.events.ClientStartEvent;
            import com.gitee.redischannel.api.cluster.RedisClusterCommandAPI;
            import com.gitee.redischannel.api.cluster.RedisClusterPubSubAPI;
            import java.util.concurrent.CompletionStage;

            public final class ApiConsumer {
                public RedisChannelAPI lifecycleApi() {
                    return RedisChannelPlugin.INSTANCE.getApi();
                }
                public CompletionStage<RedisLifecycleSnapshot> start() {
                    return RedisChannelPlugin.INSTANCE.getApi().startAsync();
                }
                public boolean isClusterEvent(ClientStartEvent event) {
                    return event.getCluster();
                }
                public CompletionStage<String> get(RedisCommandAPI api, String key) {
                    return api.executeAsync(commands -> commands.get(key));
                }
                public CompletionStage<String> clusterGet(RedisClusterCommandAPI api, String key) {
                    return api.executeClusterAsync(commands -> commands.get(key));
                }
                public CompletionStage<Void> subscribe(RedisPubSubAPI api, String channel) {
                    return api.executePubSubAsync(commands -> commands.subscribe(channel));
                }
                public CompletionStage<Void> clusterSubscribe(RedisClusterPubSubAPI api, String channel) {
                    return api.executeClusterPubSubAsync(commands -> commands.subscribe(channel));
                }
            }
            """.trimIndent()
        )
        val compiler = javax.tools.ToolProvider.getSystemJavaCompiler()
            ?: error("A full JDK is required to verify the API consumer")
        val classpath = files(apiJar, configurations.compileClasspath).asPath
        val exitCode = compiler.run(
            null,
            null,
            null,
            "--release", "8",
            "-Xlint:-options",
            "-classpath", classpath,
            "-d", outputDir.absolutePath,
            sourceFile.absolutePath
        )
        check(exitCode == 0) { "Java 8 API consumer compilation failed with exit code $exitCode" }
    }
}

val verifyReleaseVersion = tasks.register("verifyReleaseVersion") {
    doLast {
        val explicitTag = providers.gradleProperty("releaseTag").orNull
        val githubTag = System.getenv("GITHUB_REF")
            ?.takeIf { it.startsWith("refs/tags/") }
            ?.let { System.getenv("GITHUB_REF_NAME") }
        val releaseTag = explicitTag ?: githubTag
        if (!releaseTag.isNullOrBlank()) {
            check(releaseTag == "v${project.version}") {
                "Release tag $releaseTag does not match project version v${project.version}"
            }
        }
    }
}

tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>().configureEach {
    dependsOn(verifyReleaseVersion)
    dependsOn(verifyApiConsumer)
}

tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenLocal>().configureEach {
    dependsOn(verifyApiConsumer)
}

publishing {
    repositories {
        maven {
            url = uri("https://maven.mcwar.cn/releases")
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
            artifact(stableApiJar) {
                classifier = "api"
                builtBy(verifyApiConsumer)
            }
            groupId = project.group.toString()
        }
    }
}
