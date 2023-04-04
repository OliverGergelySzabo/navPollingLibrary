package com.github.oliverszabo.navpolling.model

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import java.math.BigDecimal
import java.time.LocalDate

class InvoiceData(
    val invoiceNumber: String,
    val invoiceIssueDate: LocalDate,
    val completenessIndicator: Boolean,
    val invoiceMain: InvoiceMain
) {
    class InvoiceMain(
        val invoice: Invoice
    )

    class Invoice(
        val invoiceHead: InvoiceHead,
        val invoiceSummary: InvoiceSummary
    )

    class InvoiceHead(
        val supplierInfo: SupplierInfo,
        val customerInfo: CustomerInfo? = null,
        val invoiceDetail: InvoiceDetail
    )

    class InvoiceSummary(
        val summaryNormal: SummaryNormal? = null,
        val summaryGrossData: SummaryGrossData? = null,
    )

    class SummaryNormal(
        val invoiceNetAmount: BigDecimal
    )

    class SummaryGrossData(
        val invoiceGrossAmount: BigDecimal,
    )

    class SupplierInfo(
        val supplierBankAccountNumber: String? = null,
        val supplierTaxNumber: TaxNumber
    )

    class CustomerInfo(
        val customerVatData: CustomerVatData? = null,
        val customerBankAccountNumber: String? = null
    )

    class CustomerVatData(
        val customerTaxNumber: TaxNumber? = null,
        val communityVatNumber: String? = null,
    )

    class TaxNumber(
        @field:JacksonXmlProperty(namespace = "base")
        val taxpayerId: String
    )

    class InvoiceDetail(
        val currencyCode: String,
        val paymentMethod: String? = null,
        val paymentDate: LocalDate? = null,
    )
}