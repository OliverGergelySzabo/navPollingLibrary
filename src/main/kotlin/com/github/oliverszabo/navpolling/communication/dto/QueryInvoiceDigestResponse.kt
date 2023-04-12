package com.github.oliverszabo.navpolling.communication.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.github.oliverszabo.navpolling.model.InvoiceDigest

class QueryInvoiceDigestResponse {

    lateinit var invoiceDigestResult: InvoiceDigestResult

    class InvoiceDigestResult {
        var currentPage: Int? = null
        var availablePage: Int? = null
        @field:JacksonXmlElementWrapper(useWrapping=false)
        var invoiceDigest : Array<InvoiceDigest>? = null
    }
}