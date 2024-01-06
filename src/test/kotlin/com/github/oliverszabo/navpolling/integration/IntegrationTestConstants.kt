package com.github.oliverszabo.navpolling.integration

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import java.time.Instant

object IntegrationTestConstants {
    val pollingDirection = InvoiceDirection.INBOUND
    val technicalUser = TechnicalUser("l", "p", "t", "s", pollingDirections = setOf(pollingDirection))
    const val ignoredFieldValue = "ignoredFieldValue"
    const val invoiceNumber = "invoiceNumber"
    const val supplierTaxNumber = "supplierTaxNumber"
    val now = Instant.EPOCH
}