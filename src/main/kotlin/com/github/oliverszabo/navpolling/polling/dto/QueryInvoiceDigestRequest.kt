package com.github.oliverszabo.navpolling.polling.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.github.oliverszabo.navpolling.api.InvoiceDirection
import java.time.Instant
import java.time.LocalDate

class QueryInvoiceDigestRequest(
    config: Config,
    val insDateFrom: Instant,
    val insDateTo: Instant,
    val page: Int,
    val invoiceDirection: InvoiceDirection
): NavRequest(config) {
    private class DateTimeInterval(
        val dateTimeFrom: Instant,
         val dateTimeTo: Instant
    )

    private data class InvoiceQueryParams(
        val mandatoryQueryParams : MandatoryQueryParams
    )

    private data class MandatoryQueryParams(
        val insDate: DateTimeInterval
    )

    @JacksonXmlRootElement(localName = "QueryInvoiceDigestRequest")
    private class RequestRoot (
        config: Config,
        val page: Int,
        val invoiceDirection : InvoiceDirection,
        val invoiceQueryParams: InvoiceQueryParams
    ): RootBase(config)

    override val operation = "queryInvoiceDigest"

    override fun toXml() : String{
        return generateXml(
            RequestRoot(
                config,
                page,
                invoiceDirection = invoiceDirection,
                invoiceQueryParams = InvoiceQueryParams(
                    MandatoryQueryParams(
                        insDate = DateTimeInterval(
                            dateTimeFrom = insDateFrom,
                            dateTimeTo = insDateTo
                        )
                    )
                )
            )
        )
    }
}