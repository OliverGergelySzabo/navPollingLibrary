package com.github.oliverszabo.navpolling.polling.dto

import com.github.oliverszabo.navpolling.util.decompressGzip
import com.github.oliverszabo.navpolling.util.isGzipped
import java.util.*

class QueryInvoiceDataResponse(
    // the val cannot be removed from here because that will cause an exception
    // during deserialization (most probably due to a jackson bug)
    val invoiceDataResult: InvoiceDataResult? = null
) {
    val invoiceDataXml: String? = if (invoiceDataResult != null) {
        var decodedData = Base64.getDecoder().decode(invoiceDataResult.invoiceData)
        if(isGzipped(decodedData)) {
            decodedData = decompressGzip(decodedData)
        }
        String(decodedData)
    } else {
        null
    }

    class InvoiceDataResult(
        val invoiceData: String
    )
}