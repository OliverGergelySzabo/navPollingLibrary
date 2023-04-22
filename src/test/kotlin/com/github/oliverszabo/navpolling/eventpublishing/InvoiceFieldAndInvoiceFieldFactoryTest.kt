package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.createXmlMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Field
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate

class InvoiceFieldAndInvoiceFieldFactoryTest {
    private val invoiceData = createXmlMapper().readValue(
        Paths.get("src","test","resources", "Peldaszamlak_v3.0", "Belfoldi devizas szamla.xml").toFile(),
        InvoiceData::class.java
    )
    private val invoiceDigest = InvoiceDigest(
        invoiceNumber = "invoiceNumber",
        invoiceOperation = "invoiceOperation",
        invoiceCategory = "invoiceCategory",
        invoiceIssueDate = LocalDate.now(),
        supplierTaxNumber = "supplierTaxNumber",
        supplierName = "supplierName",
        insDate = Instant.now()
    )
    private val invoiceFieldFactory = InvoiceFieldFactory()

    @Test
    fun invoiceFieldFactoryGetInvoiceFieldThrowExceptionForAmbiguousShortNames() {
        assertThrows<IllegalArgumentException> {
            invoiceFieldFactory.getInvoiceField("communityVatNumber")
        }
    }

    @Test
    fun invoiceFieldFactoryGetInvoiceFieldReturnsDigestFieldForDigestShortName() {
        val shortName = "supplierName"
        assertInvoiceField(
            "InvoiceDigest.$shortName",
            InvoiceDigest::class.java.declaredFields.find { it.name == shortName }!!,
            expectedIsDigestField = true,
            invoiceFieldFactory.getInvoiceField(shortName)!!
        )
    }

    @Test
    fun invoiceFieldFactoryGetInvoiceFieldReturnsDigestFieldForDigestLongName() {
        val shortName = "supplierName"
        val longName = "InvoiceDigest.$shortName"
        assertInvoiceField(
            longName,
            InvoiceDigest::class.java.declaredFields.find { it.name == shortName }!!,
            expectedIsDigestField = true,
            invoiceFieldFactory.getInvoiceField(longName)!!
        )
    }

    @Test
    fun invoiceFieldFactoryGetInvoiceFieldReturnsCorrectFieldForNonAmbiguousShortName() {
        val shortName = "supplierBankAccountNumber"
        assertInvoiceField(
            "InvoiceData.invoiceMain.invoice.invoiceHead.supplierInfo.supplierBankAccountNumber",
            InvoiceData.SupplierInfo::class.java.declaredFields.find { it.name == shortName }!!,
            expectedIsDigestField = false,
            invoiceFieldFactory.getInvoiceField(shortName)!!
        )
    }

    @Test
    fun invoiceFieldFactoryGetInvoiceFieldReturnsCorrectFieldForLongName() {
        // unambiguous short name
        var shortName = "invoiceGrossAmountHUF"
        var longName = "InvoiceData.invoiceMain.invoice.invoiceSummary.summaryGrossData.$shortName"
        assertInvoiceField(
            longName,
            InvoiceData.SummaryGrossData::class.java.declaredFields.find { it.name == shortName }!!,
            expectedIsDigestField = false,
            invoiceFieldFactory.getInvoiceField(longName)!!
        )

        // ambiguous short name
        shortName = "city"
        longName = "InvoiceData.invoiceMain.invoice.invoiceHead.fiscalRepresentativeInfo.fiscalRepresentativeAddress.detailedAddress.$shortName"
        assertInvoiceField(
            longName,
            InvoiceData.DetailedAddress::class.java.declaredFields.find { it.name == shortName }!!,
            expectedIsDigestField = false,
            invoiceFieldFactory.getInvoiceField(longName)!!
        )
    }

    @Test
    fun invoiceFieldFactoryGetInvoiceFieldReturnsNullForNonExistingField() {
        assertNull(invoiceFieldFactory.getInvoiceField("nonExisting"))
    }

    @Test
    fun invoiceFieldGetValueThrowsExceptionForInvalidModelClasses() {
        assertThrows<IllegalArgumentException> {
            invoiceFieldFactory.getInvoiceField("supplierBankAccountNumber")!!.getValue(invoiceDigest, String::class.java)
        }

        assertThrows<IllegalArgumentException>() {
            invoiceFieldFactory.getInvoiceField("supplierName")!!.getValue(invoiceData, String::class.java)
        }
    }

    private fun assertInvoiceField(expectedLongName: String, expectedJavaField: Field, expectedIsDigestField: Boolean, invoiceField: InvoiceField) {
        assertEquals(expectedLongName, invoiceField.longName)
        assertEquals(expectedJavaField, invoiceField.javaField)
        assertEquals(expectedIsDigestField, invoiceField.isInvoiceDigestField)
    }
}