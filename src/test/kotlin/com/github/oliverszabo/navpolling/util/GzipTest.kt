package com.github.oliverszabo.navpolling.util

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.*

class GzipTest {
    private val uncompressedData = Paths.get("src", "test", "resources", "Gzip", "UncompressedFile.txt").toFile().readBytes()
    private val compressedData = Paths.get("src", "test", "resources", "Gzip", "CompressedFile.gz").toFile().readBytes()

    @Test
    fun isGzippedReturnsTrueForGzippedData() {
        assertTrue(isGzipped(compressedData))
    }

    @Test
    fun isGzippedReturnsFalseForUncompressedData() {
        assertFalse(isGzipped(uncompressedData))
    }

    @Test
    fun decompressGzipCorrectlyDecompressesGzippedData() {
        assertEquals(String(uncompressedData), String(decompressGzip(compressedData)))
    }
}