package com.github.oliverszabo.navpolling.model

import com.github.oliverszabo.navpolling.util.createXmlMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Paths

class InvoiceDataTest {
    @Test
    fun jacksonCorrectlyDeserializesInvoiceData() {
        val mapper = createXmlMapper()

        Paths
            .get("src","test","resources", "Peldaszamlak_v3.0")
            .toFile()
            .listFiles()!!
            .filter { !it.isDirectory && it.exists() && it.extension == "xml" }
            .forEach { file ->
                // no assertion needed we only test that the deserialization does not throw an exception
                try {
                    mapper.readValue(file, InvoiceData::class.java)
                } catch (e: Exception) {
                    println("Error in parsing file: ${file.name}")
                    throw e
                }
            }
    }
}