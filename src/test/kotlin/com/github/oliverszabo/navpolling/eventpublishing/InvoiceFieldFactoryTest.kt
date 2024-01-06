package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryException
import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.assertThrownException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Field
import java.math.BigDecimal

class InvoiceFieldFactoryTest {
    private val invoiceFieldFactory = InvoiceFieldFactory()
    private val targetField = mockk<Field>(relaxed = true)

    @BeforeEach
    fun beforeEach() {
        every { targetField.type } returns String::class.java
    }

    @Test
    fun getInvoiceFieldThrowsExceptionForAmbiguousShortNames() {
        val shortName = "communityVatNumber"
        assertThrownException<IllegalArgumentException>(
            InvoiceFieldFactory.AMBIGUOUS_SHORT_NAME_ERROR_TEMPLATE.format(
                shortName,
                "InvoiceData.invoiceMain.invoice.invoiceHead.supplierInfo.communityVatNumber', " +
                        "'InvoiceData.invoiceMain.invoice.invoiceHead.customerInfo.customerVatData.communityVatNumber"
            )
        ) {
            invoiceFieldFactory.getInvoiceField(shortName, targetField)
        }
    }

    @Test
    fun getInvoiceFieldThrowsExceptionWhenNoFieldIsFoundForName() {
        val name = "nonExisting"
        assertThrownException<IllegalArgumentException>(InvoiceFieldFactory.INVOICE_FIELD_NOT_FOUND_ERROR_TEMPLATE.format(name)) {
            invoiceFieldFactory.getInvoiceField(name, targetField)
        }
    }

    @Test
    fun getInvoiceFieldThrowsExceptionWhenNoFieldConvertibleToTargetFieldFound() {
        every { targetField.type } returns BigDecimal::class.java
        val shortName = "supplierName"
        assertThrownException<IllegalArgumentException>(
            InvoiceFieldFactory.INVOICE_FIELD_NOT_CONVERTIBLE_TO_TARGET_FIELD_ERROR_TEMPLATE.format(
                targetField.name,
                targetField.type,
                String::class.java
            )
        ) {
            invoiceFieldFactory.getInvoiceField(shortName, targetField)
        }
    }

    @Test
    fun getInvoiceFieldReturnsDigestFieldForDigestShortName() {
        val shortName = "supplierName"
        assertInvoiceField(
            "InvoiceDigest.$shortName",
            InvoiceDigest::class.java.declaredFields.find { it.name == shortName }!!,
            true,
            invoiceFieldFactory.getInvoiceField(shortName, targetField)
        )
    }

    @Test
    fun getInvoiceFieldReturnsDigestFieldForDigestLongName() {
        val shortName = "supplierName"
        val longName = "InvoiceDigest.$shortName"
        assertInvoiceField(
            longName,
            InvoiceDigest::class.java.declaredFields.find { it.name == shortName }!!,
            true,
            invoiceFieldFactory.getInvoiceField(longName, targetField)
        )
    }

    @Test
    fun getInvoiceFieldReturnsCorrectFieldForNonAmbiguousShortName() {
        val shortName = "supplierBankAccountNumber"
        assertInvoiceField(
            "InvoiceData.invoiceMain.invoice.invoiceHead.supplierInfo.supplierBankAccountNumber",
            InvoiceData.SupplierInfo::class.java.declaredFields.find { it.name == shortName }!!,
            false,
            invoiceFieldFactory.getInvoiceField(shortName, targetField)
        )
    }

    @Test
    fun getInvoiceFieldReturnsCorrectFieldForLongName() {
        // unambiguous short name
        var shortName = "invoiceGrossAmountHUF"
        var longName = "InvoiceData.invoiceMain.invoice.invoiceSummary.summaryGrossData.$shortName"
        assertInvoiceField(
            longName,
            InvoiceData.SummaryGrossData::class.java.declaredFields.find { it.name == shortName }!!,
            false,
            invoiceFieldFactory.getInvoiceField(longName, targetField)
        )

        // ambiguous short name
        shortName = "city"
        longName = "InvoiceData.invoiceMain.invoice.invoiceHead.fiscalRepresentativeInfo.fiscalRepresentativeAddress.detailedAddress.$shortName"
        assertInvoiceField(
            longName,
            InvoiceData.DetailedAddress::class.java.declaredFields.find { it.name == shortName }!!,
            false,
            invoiceFieldFactory.getInvoiceField(longName, targetField)
        )
    }

    @Test
    fun getInvoiceFieldReturnsReturnsDigestFieldForShortNameOnlyIfItsCompatibleWithTheTargetType() {
        // targetField type compatible with InvoiceDigest field
        val shortName = "supplierTaxNumber"
        assertInvoiceField(
            "InvoiceDigest.$shortName",
            InvoiceDigest::class.java.declaredFields.find { it.name == shortName }!!,
            true,
            invoiceFieldFactory.getInvoiceField(shortName, targetField)
        )

        // targetField type compatible with InvoiceData field
        every { targetField.type } returns InvoiceData.TaxNumber::class.java
        assertInvoiceField(
            "InvoiceData.invoiceMain.invoice.invoiceHead.supplierInfo.$shortName",
            InvoiceData.SupplierInfo::class.java.declaredFields.find { it.name == shortName }!!,
            false,
            invoiceFieldFactory.getInvoiceField(shortName, targetField)
        )
    }

    private fun assertInvoiceField(expectedLongName: String, expectedJavaField: Field, isDigestFieldExpected: Boolean, invoiceField: InvoiceField) {
        assertEquals(expectedLongName, invoiceField.longName)
        assertEquals(expectedJavaField, invoiceField.javaField)
        val expectedType = if(isDigestFieldExpected) InvoiceDigestField::class.java else InvoiceDataField::class.java
        assertEquals(expectedType, invoiceField.javaClass)
    }
}