package com.github.oliverszabo.navpolling.polling.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.github.oliverszabo.navpolling.api.InvoiceDirection

class QueryInvoiceDataRequest(
    config: Config,
    val invoiceNumber: String,
    val invoiceDirection: InvoiceDirection,
    val supplierTaxNumber: String? = null
): NavRequest(config) {
    private data class InvoiceNumberQuery(
        val invoiceNumber : String,
        val invoiceDirection : InvoiceDirection,
        val supplierTaxNumber : String? = null
    )

    @JacksonXmlRootElement(localName = "QueryInvoiceDataRequest")
    private class RequestRoot(
        config: Config,
        val invoiceNumberQuery: InvoiceNumberQuery
    ) : RootBase(config)

    override val operation = "queryInvoiceData"

    override fun toXml(): String {
        return generateXml(RequestRoot(config, InvoiceNumberQuery(invoiceNumber, invoiceDirection, supplierTaxNumber)))
    }
}