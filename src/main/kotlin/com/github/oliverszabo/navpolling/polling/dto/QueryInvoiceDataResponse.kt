package com.github.oliverszabo.navpolling.polling.dto

import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.util.createXmlMapper
import java.util.*

class QueryInvoiceDataResponse(
    val invoiceDataResult: InvoiceDataResult? = null
) {
    val invoiceData: InvoiceData? = if (invoiceDataResult != null) {
            createXmlMapper().readValue(Base64.getDecoder().decode(invoiceDataResult.invoiceData), InvoiceData::class.java)
    } else {
        null
    }

    data class InvoiceDataResult(
        var invoiceData : String? = null
    )

    fun getSupplierBankAccountNumber() : String? {
        return invoiceData?.invoiceMain?.invoice?.invoiceHead?.supplierInfo?.supplierBankAccountNumber
    }
}