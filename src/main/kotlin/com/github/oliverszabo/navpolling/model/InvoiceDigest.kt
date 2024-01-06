package com.github.oliverszabo.navpolling.model

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate

class InvoiceDigest(
    val invoiceNumber: String,
    val batchIndex: BigInteger? = null,
    val invoiceOperation: String,
    val invoiceCategory: String,
    val invoiceIssueDate: LocalDate,
    val supplierTaxNumber: String,
    val supplierGroupMemberTaxNumber: String? = null,
    val supplierName: String,
    val customerTaxNumber: String? = null,
    val customerGroupMemberTaxNumber: String? = null,
    val customerName: String? = null,
    val paymentMethod: String? = null,
    val paymentDate: LocalDate? = null,
    val invoiceAppearance: String? = null,
    val source: String? = null,
    val invoiceDeliveryDate: LocalDate? = null,
    val currency: String? = null,
    val invoiceNetAmount: BigDecimal? = null,
    val invoiceNetAmountHUF: BigDecimal? = null,
    val invoiceVatAmount: BigDecimal? = null,
    val invoiceVatAmountHUF: BigDecimal? = null,
    val transactionId: String? = null,
    val index: BigInteger? = null,
    val originalInvoiceNumber: String? = null,
    val modificationIndex: BigInteger? = null,
    val insDate: Instant,
    val completenessIndicator: Boolean? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvoiceDigest

        if (invoiceNumber != other.invoiceNumber) return false
        if (supplierTaxNumber != other.supplierTaxNumber) return false

        return true
    }

    override fun hashCode(): Int {
        var result = invoiceNumber.hashCode()
        result = 31 * result + supplierTaxNumber.hashCode()
        return result
    }

    override fun toString(): String {
        return "${this::class.simpleName}(invoiceNumber=$invoiceNumber, supplierTaxNumber=$supplierTaxNumber)"
    }
}