package com.github.oliverszabo.navpolling.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class InvoiceDigest {
    var invoiceNumber : String? = null
    var invoiceOperation : String? = null
    var invoiceCategory : String? = null
    var invoiceIssueDate : LocalDate? = null
    var supplierTaxNumber : String? = null
    var supplierName : String? = null
    var customerTaxNumber : String? = null
    var customerName : String? = null
    var paymentMethod : String? = null
    var paymentDate : LocalDate? = null
    var invoiceAppearance : String? = null
    var source : String? = null
    var invoiceDeliveryDate : LocalDate? = null
    var currency : String? = null
    var invoiceNetAmount : BigDecimal? = null
    var invoiceNetAmountHUF : BigDecimal? = null
    var invoiceVatAmount : BigDecimal? = null
    var invoiceVatAmountHUF : BigDecimal? = null
    var transactionId : String? = null
    var index : Int? = null
    var insDate : Instant? = null
    var completenessIndicator : String? = null
}