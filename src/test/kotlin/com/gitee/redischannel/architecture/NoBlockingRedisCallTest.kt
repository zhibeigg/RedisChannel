package com.gitee.redischannel.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class NoBlockingRedisCallTest {

    @Test
    fun `production source contains no blocking Redis calls`() {
        val forbidden = listOf(
            ".join(",
            ".block(",
            ".sync()",
            "borrowObject(",
            "connectPubSub()",
            "MasterReplica.connect(",
            "client.shutdown("
        )
        val violations = mutableListOf<String>()
        Files.walk(Paths.get("src/main/kotlin")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.forEach { path ->
                val content = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                forbidden.filter(content::contains).forEach { token ->
                    violations += "$path contains $token"
                }
            }
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }
}
