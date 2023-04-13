package com.github.oliverszabo.navpolling.communication.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.github.oliverszabo.navpolling.api.InvoiceDirection
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class QueryInvoiceDigestRequest(
    private val insDateFrom: Instant,
    private val insDateTo: Instant,
    private val page: Int,
    private val invoiceDirection: InvoiceDirection,
    private val paymentMethod: PaymentMethod? = null,
    private val currency: String? = null,
    private val minPaymentDeadline: LocalDate? = null
): RequestBase() {
    private data class DateInterval(
        val dateFrom : String,
        val dateTo: String
    )

    private data class DateTimeInterval(
        val dateTimeFrom : String,
        val dateTimeTo: String
    )

    private data class InvoiceQueryParams(
        val mandatoryQueryParams : MandatoryQueryParams,
        val additionalQueryParams: AdditionalQueryParams? = null,
        val relationalQueryParams: RelationalQueryParams? = null
    )

    private data class MandatoryQueryParams(
        //val invoiceIssueDate : DateInterval,
        val insDate: DateTimeInterval,
        //val originalInvoiceNumber : String
    )

    private data class AdditionalQueryParams(
        val paymentMethod: PaymentMethod? = null,
        val currency: String? = null
    )

    private data class RelationalQueryParams(
        val paymentDate: RelationalQueryParam
    )

    @JacksonXmlRootElement(localName = "QueryInvoiceDigestRequest")
    private class RequestRoot (
        config: Config,
        val page: Int,
        val invoiceDirection : InvoiceDirection,
        val invoiceQueryParams: InvoiceQueryParams
    ): RootBase(config)

    override val command = "queryInvoiceDigest"

    override fun toXml(config: Config) : String{
        val additionalQueryParams = if(paymentMethod != null || currency != null)
            AdditionalQueryParams(paymentMethod, currency) else null
        val relationalQueryParams = if(minPaymentDeadline != null)
            RelationalQueryParams(RelationalQueryParam(QueryOperator.GTE, minPaymentDeadline.toString())) else null

        return generateXml(
            RequestRoot(
                config,
                page,
                invoiceDirection = invoiceDirection,
                invoiceQueryParams = InvoiceQueryParams(
                    MandatoryQueryParams(
                        insDate = DateTimeInterval(
                            dateTimeFrom = insDateFrom.truncatedTo(ChronoUnit.SECONDS).toString(),
                            dateTimeTo = insDateTo.truncatedTo(ChronoUnit.SECONDS).toString()
                        )
                    ),
                    additionalQueryParams,
                    relationalQueryParams
                )
            )
        )
    }
}