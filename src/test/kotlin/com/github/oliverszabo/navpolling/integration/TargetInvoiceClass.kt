package com.github.oliverszabo.navpolling.integration

import com.github.oliverszabo.navpolling.api.annotation.IgnoredField
import com.github.oliverszabo.navpolling.api.annotation.InvoiceFieldMapping
import java.math.BigInteger
import java.time.LocalDate

class TargetInvoiceClass(
    @InvoiceFieldMapping("invoiceNumber")
    val mappedField: String,
    val invoiceGrossAmount: BigInteger,
    val paymentDate: LocalDate,
    val supplierBankAccountNumber: String,
    @IgnoredField
    val ignoredField: String = IntegrationTestConstants.ignoredFieldValue,
)