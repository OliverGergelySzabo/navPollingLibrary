package com.github.oliverszabo.navpolling.polling.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.github.oliverszabo.navpolling.model.InvoiceDigest

class QueryInvoiceDigestResponse {

    lateinit var invoiceDigestResult: InvoiceDigestResult

    class InvoiceDigestResult(
        var currentPage: Int,
        var availablePage: Int,
        @field:JacksonXmlElementWrapper(useWrapping=false)
        var invoiceDigest : List<InvoiceDigest>? = null
    )
}