package com.gitee.redischannel.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileUtilTest {

    @Nested
    inner class GetFilesTest {

        @Test
        fun `should return empty list for non-existent file`() {
            val nonExistent = File("non_existent_path_12345")
            val result = getFiles(nonExistent)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return single yml file`(@TempDir tempDir: File) {
            val ymlFile = File(tempDir, "test.yml")
            ymlFile.writeText("key: value")

            val result = getFiles(ymlFile)
            assertEquals(1, result.size)
            assertEquals(ymlFile, result[0])
        }

        @Test
        fun `should ignore non-yml files`(@TempDir tempDir: File) {
            val txtFile = File(tempDir, "test.txt")
            txtFile.writeText("hello")

            val result = getFiles(txtFile)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should find yml files in directory`(@TempDir tempDir: File) {
            File(tempDir, "a.yml").writeText("a: 1")
            File(tempDir, "b.yml").writeText("b: 2")
            File(tempDir, "c.txt").writeText("c: 3")

            val result = getFiles(tempDir)
            assertEquals(2, result.size)
            assertTrue(result.all { it.name.endsWith(".yml") })
        }

        @Test
        fun `should find yml files in nested directories`(@TempDir tempDir: File) {
            val subDir = File(tempDir, "sub")
            subDir.mkdirs()
            File(tempDir, "root.yml").writeText("root: 1")
            File(subDir, "nested.yml").writeText("nested: 2")
            File(subDir, "ignored.json").writeText("{}")

            val result = getFiles(tempDir)
            assertEquals(2, result.size)
            val names = result.map { it.name }.toSet()
            assertTrue(names.contains("root.yml"))
            assertTrue(names.contains("nested.yml"))
        }

        @Test
        fun `should return empty list for empty directory`(@TempDir tempDir: File) {
            val emptyDir = File(tempDir, "empty")
            emptyDir.mkdirs()

            val result = getFiles(emptyDir)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should handle deeply nested directories`(@TempDir tempDir: File) {
            val deep = File(tempDir, "a/b/c")
            deep.mkdirs()
            File(deep, "deep.yml").writeText("deep: true")

            val result = getFiles(tempDir)
            assertEquals(1, result.size)
            assertEquals("deep.yml", result[0].name)
        }

        @Test
        fun `should handle directory with only non-yml files`(@TempDir tempDir: File) {
            File(tempDir, "a.txt").writeText("a")
            File(tempDir, "b.json").writeText("{}")
            File(tempDir, "c.xml").writeText("<c/>")

            val result = getFiles(tempDir)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should handle mixed nested structure`(@TempDir tempDir: File) {
            val sub1 = File(tempDir, "sub1")
            val sub2 = File(tempDir, "sub2")
            sub1.mkdirs()
            sub2.mkdirs()

            File(sub1, "config1.yml").writeText("c1: 1")
            File(sub1, "readme.md").writeText("# readme")
            File(sub2, "config2.yml").writeText("c2: 2")
            File(sub2, "config3.yml").writeText("c3: 3")

            val result = getFiles(tempDir)
            assertEquals(3, result.size)
            assertTrue(result.all { it.name.endsWith(".yml") })
        }
    }
}
