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
    val invoiceDirection: InvoiceDirection,
    val paymentMethod: PaymentMethod? = null,
    val currency: String? = null,
    val minPaymentDeadline: LocalDate? = null
): NavRequest(config) {
    private data class DateInterval(
        val dateFrom : String,
        val dateTo: String
    )

    private class DateTimeInterval(
        dateTimeFrom: Instant,
        dateTimeTo: Instant
    ) {
        // jackson converts Instant objects to timestamps instead of is date format
        // so the toString method has to be called for correct serialization
        val dateTimeFrom : String = dateTimeFrom.toString()
        val dateTimeTo: String = dateTimeTo.toString()
    }

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

    override fun toXml() : String{
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
                            dateTimeFrom = insDateFrom,
                            dateTimeTo = insDateTo
                        )
                    ),
                    additionalQueryParams,
                    relationalQueryParams
                )
            )
        )
    }
}