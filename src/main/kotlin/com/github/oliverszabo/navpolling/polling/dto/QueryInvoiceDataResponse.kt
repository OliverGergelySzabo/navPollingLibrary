package com.github.oliverszabo.navpolling.polling.dto

import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.util.createXmlMapper
import com.github.oliverszabo.navpolling.util.decompressGzip
import com.github.oliverszabo.navpolling.util.isGzipped
import java.util.*

class QueryInvoiceDataResponse(
    invoiceDataResult: InvoiceDataResult? = null
) {
    val invoiceData: InvoiceData? = if (invoiceDataResult != null) {
        var decodedData = Base64.getDecoder().decode(invoiceDataResult.invoiceData)
        if(isGzipped(decodedData)) {
            decodedData = decompressGzip(decodedData)
        }
        createXmlMapper().readValue(decodedData, InvoiceData::class.java)
    } else {
        null
    }

    data class InvoiceDataResult(
        var invoiceData : String? = null
    )
}