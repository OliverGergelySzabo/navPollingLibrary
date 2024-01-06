package com.github.oliverszabo.navpolling.util

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.model.InvoiceData
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Paths
import java.time.Instant
import java.util.Base64

fun <E, A> assertListsContainSameElements(expectedList: List<E>, actualList: List<A>, comparator: (E, A) -> Boolean) {
    assertEquals(expectedList.size, actualList.size)
    val mutableActualList = actualList.toMutableList()
    expectedList.forEach { expectedElement ->
        val index = mutableActualList.indexOfFirst { comparator(expectedElement, it) }
        assertNotEquals(-1, index, "Element '$expectedElement' was not found in actual list")
        mutableActualList.removeAt(index)
    }
}

fun <E, A> assertSetsContainSameElements(expectedSet: Set<E>, actualSet: Set<A>, comparator: (E, A) -> Boolean) {
    assertListsContainSameElements(expectedSet.toList(), actualSet.toList(), comparator)
}

inline fun <reified T: Throwable>assertThrownException(expectedMessage: String, executable: () -> Unit) {
    val exception: T = assertThrows(executable)
    assertEquals(expectedMessage, exception.message)
}

fun assertEmpty(collection: Collection<*>) {
    assertTrue(collection.isEmpty())
}

fun createTechnicalUser(
    login: String,
    pollingCompleteUntil: Instant? = null,
): TechnicalUser {
    return TechnicalUser(login, "p", "t", "s", InvoiceDirection.values().toSet(), pollingCompleteUntil)
}

fun base64Encode(s: String): String {
    return base64Encode(s.toByteArray())
}

fun base64Encode(b: ByteArray): String {
    return String(Base64.getEncoder().encode(b))
}

object NavDataCreator {
    private val invoiceDataTemplate = Paths.get("src", "test", "resources", "templates/InvoiceDataTemplate.xml").toFile().readText()
    private val queryInvoiceDataResponseTemplate =
        Paths.get("src", "test", "resources", "templates/QueryInvoiceDataResponseTemplate.xml").toFile().readText()
    private val queryInvoiceDigestResponseTemplate =
        Paths.get("src", "test", "resources", "templates/QueryInvoiceDigestResponseTemplate.xml").toFile().readText()
    private val errorResponseTemplate =
        Paths.get("src", "test", "resources", "templates/ErrorResponseTemplate.xml").toFile().readText()
    private val mapper = createXmlMapper()

    val batchInvoiceData = String(
            Base64
                .getEncoder()
                .encode(
                Paths.get("src", "test", "resources", "Peldaszamlak_v3.0", "Tobb szamla modositasa egy okirattal.xml.disabled")
                    .toFile()
                    .readBytes()
            )
    )

    fun createInvoiceData(invoiceNumber: String, supplierTaxNumber: String): InvoiceData {
        return mapper.readValue(invoiceDataTemplate.format(invoiceNumber, supplierTaxNumber), InvoiceData::class.java)
    }

    fun createEncodedInvoiceData(invoiceNumber: String, supplierTaxNumber: String): String {
       return base64Encode(mapper.writeValueAsBytes(createInvoiceData(invoiceNumber, supplierTaxNumber)))
    }

    fun createQueryInvoiceDataResponse(invoiceNumber: String, supplierTaxNumber: String): String {
        return createQueryInvoiceDataResponse(mapper.writeValueAsString(createInvoiceData(invoiceNumber, supplierTaxNumber)))
    }

    fun createQueryInvoiceDataResponse(invoiceData: String): String {
        return queryInvoiceDataResponseTemplate.format(base64Encode(invoiceData))
    }

    fun createQueryInvoiceDigestResponse(invoiceNumber: String, supplierTaxNumber: String): String {
        return queryInvoiceDigestResponseTemplate.format(invoiceNumber, supplierTaxNumber)
    }

    fun createErrorResponse(funcCode: String, errorCode: String, message: String): String {
        return errorResponseTemplate.format(funcCode, errorCode, message)
    }
}
