package com.github.oliverszabo.navpolling.model

import com.github.oliverszabo.navpolling.util.TypeUtils
import com.github.oliverszabo.navpolling.util.createXmlMapper
import com.github.oliverszabo.navpolling.util.firstActualTypeArgument
import com.github.oliverszabo.navpolling.util.forEachFieldsRecursively
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate

class InvoiceDataAndInvoiceDigestTest {
    @Test
    fun jacksonCorrectlyDeserializesInvoiceData() {
        val mapper = createXmlMapper()

        Paths
            .get("src", "test", "resources", "Peldaszamlak_v3.0")
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

    @Test
    fun invoiceDataAndDigestShouldOnlyContainBooleanStringBigIntegerBigDecimalLocalDateAndInstantSimpleFields() {
        assertClassOnlyHasBooleanStringBigIntegerBigDecimalLocalDateAndInstantSimpleFields(InvoiceData::class.java)
        assertClassOnlyHasBooleanStringBigIntegerBigDecimalLocalDateAndInstantSimpleFields(InvoiceDigest::class.java)
    }

    private fun assertClassOnlyHasBooleanStringBigIntegerBigDecimalLocalDateAndInstantSimpleFields(cls: Class<*>) {
        val allowedPrimitiveFieldTypes: List<Class<*>> = listOf(
            String::class.java,
            BigInteger::class.java,
            BigDecimal::class.java,
            LocalDate::class.java,
            Instant::class.java
        )
            .plus(TypeUtils.PrimitiveType.BOOLEAN.classes)

        cls.forEachFieldsRecursively { (field, _) ->
            if(!field.type.canonicalName.startsWith("com.github.oliverszabo.navpolling") && field.firstActualTypeArgument() == null) {
                assertTrue(allowedPrimitiveFieldTypes.contains(field.type))
            }
        }
    }
}