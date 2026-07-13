package com.gitee.redischannel.compat

import com.gitee.redischannel.RedisChannelPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarFile

class Java8CompatibilityTest {

    @Test
    fun `all plugin classes target Java 8 bytecode`() {
        val location = Paths.get(RedisChannelPlugin::class.java.protectionDomain.codeSource.location.toURI())
        var count = 0
        if (Files.isDirectory(location)) {
            Files.walk(location).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                    .forEach { path ->
                        Files.newInputStream(path).use { verifyJava8Class(DataInputStream(it), path.toString()) }
                        count++
                    }
            }
        } else {
            JarFile(location.toFile()).use { jar ->
                jar.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }
                    .forEach { entry ->
                        jar.getInputStream(entry).use { verifyJava8Class(DataInputStream(it), entry.name) }
                        count++
                    }
            }
        }
        assertTrue(count > 0, "No production classes were checked")
    }

    private fun verifyJava8Class(input: DataInputStream, name: String) {
        assertEquals(0xCAFEBABE.toInt(), input.readInt(), name)
        input.readUnsignedShort()
        assertTrue(input.readUnsignedShort() <= 52, "$name does not target Java 8")
    }
}
