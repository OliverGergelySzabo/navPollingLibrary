package com.github.oliverszabo.navpolling.communication.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.github.oliverszabo.navpolling.api.InvoiceDirection

class QueryInvoiceDataRequest(
    val invoiceNumber: String,
    val invoiceDirection: InvoiceDirection,
    val supplierTaxNumber: String? = null
) : RequestBase() {

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

    override val command = "queryInvoiceData"

    override fun getXml(config: Config): String {
        return generateXml(RequestRoot(config, InvoiceNumberQuery(invoiceNumber, invoiceDirection, supplierTaxNumber)))
    }
}