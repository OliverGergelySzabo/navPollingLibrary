package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.util.NavDataCreator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaField

class InvoiceDataFieldTest {
    private val invoiceFieldFactory = InvoiceFieldFactory()
    private val invoiceData = NavDataCreator.createInvoiceData("invoiceNumber", "supplierTaxNumber")

    @Test
    fun `getField returns correct value`() {
        // root level field
        var invoiceField = invoiceFieldFactory.getInvoiceField("InvoiceData.invoiceNumber", TargetFieldClass::primitiveTargetField.javaField!!)
        assertEquals(invoiceData.invoiceNumber, invoiceField.getValue(invoiceData))

        // non-root level field
        invoiceField = invoiceFieldFactory.getInvoiceField("supplierBankAccountNumber", TargetFieldClass::primitiveTargetField.javaField!!)
        assertEquals(invoiceData.invoiceMain.invoice.invoiceHead.supplierInfo.supplierBankAccountNumber, invoiceField.getValue(invoiceData))

        // non-root level field with null parent, this should make all child fields null as well
        invoiceField = invoiceFieldFactory.getInvoiceField("fiscalRepresentativeName", TargetFieldClass::primitiveTargetField.javaField!!)
        assertNull(invoiceField.getValue(invoiceData))
    }

    private class TargetFieldClass(
        val primitiveTargetField: String?
    )
}