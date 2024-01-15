package com.github.oliverszabo.navpolling.polling

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.polling.dto.*
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.calculateInvoiceDirection
import com.github.oliverszabo.navpolling.util.createXmlMapper
import com.github.oliverszabo.navpolling.util.isTruncatedTo
import com.github.oliverszabo.navpolling.util.plusDays
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class NavQueryService(
    private val librarySettings: LibrarySettings
) {
    companion object {
        const val MAX_NUMBER_OF_DAYS_IN_REQUEST = 34L
        const val BATCH_INVOICE_SKIPPED_MESSAGE_TEMPLATE
            = "invoice(invoiceNumber=%s,supplierTaxNumber=%s) skipped due to being batch invoice which is currently unsupported"
        private val log = LoggerFactory.getLogger(NavQueryService::class.java)
    }

    private val xmlMapper = createXmlMapper()

    suspend fun fetchInvoiceDigestsAndData(
        technicalUser: TechnicalUser,
        to: Instant
    ): List<Pair<InvoiceDigest, InvoiceData>> {
        //TODO: make this optionally obey nav rate limiting rules
        val client = NavClient(librarySettings.requestTimeout)
        return fetchInvoiceDigests(technicalUser, to)
            .mapNotNull { invoiceDigest ->
                val invoiceData  = fetchInvoiceData(client, NavTechnicalUser.from(technicalUser), invoiceDigest)
                    ?: return@mapNotNull null
                Pair(invoiceDigest, invoiceData)
            }
    }

    suspend fun fetchInvoiceDigests(
        technicalUser: TechnicalUser,
        to: Instant
    ): List<InvoiceDigest> {
        if(technicalUser.pollingCompleteUntil == null || technicalUser.pollingCompleteUntil > to) {
            throw IllegalArgumentException("The 'pollingCompleteUntil' field of the given TechnicalUser must be before the 'to' param")
        }
        if(!technicalUser.pollingCompleteUntil.isTruncatedTo(ChronoUnit.SECONDS) || !to.isTruncatedTo(ChronoUnit.SECONDS)) {
            throw IllegalArgumentException("The 'to' param and the 'pollingCompleteUntil' filed of the given TechnicalUser users must be truncated to seconds")
        }

        val client = NavClient(librarySettings.requestTimeout)

        return createBounds(technicalUser.pollingCompleteUntil, to).flatMap { (from, to) ->
                technicalUser.pollingDirections.map { direction ->
                    fetchInvoiceDigestsForPeriod(
                        client,
                        NavTechnicalUser.from(technicalUser),
                        direction,
                        from,
                        to
                    )
                }
            }
            .flatten()
            // removing invoices on the upper bound, as this function has an exclusive upper bound
            // while the NAV API has an inclusive upper bound
            .filter { it.insDate != to }
            // removing potential duplicates caused by overlapping boundaries
            .distinct()
    }

    private fun createBounds(from: Instant, to: Instant): List<Pair<Instant, Instant>> {
        val bounds = mutableListOf<Pair<Instant, Instant>>()
        var currentBoundFrom = from
        do {
            val currentBoundTo = minOf(to, currentBoundFrom.plusDays(MAX_NUMBER_OF_DAYS_IN_REQUEST))
            bounds.add(Pair(currentBoundFrom, currentBoundTo))
            currentBoundFrom = currentBoundTo
        } while (to > currentBoundTo)
        return bounds
    }

    private suspend fun fetchInvoiceDigestsForPeriod(
        client: NavClient,
        navTechnicalUser: NavTechnicalUser,
        invoiceDirection: InvoiceDirection,
        from: Instant,
        to: Instant
    ): List<InvoiceDigest> {
        val firstPage = fetchInvoiceDigestPage(client, navTechnicalUser, invoiceDirection, from, to, 1)
        val invoiceDigests = mutableListOf<InvoiceDigest>()
        invoiceDigests.addAll(firstPage.invoiceDigest ?: emptyList())
        val numberOfPages = firstPage.availablePage
        for(page in 2..numberOfPages) {
            invoiceDigests.addAll(fetchInvoiceDigestPage(client, navTechnicalUser, invoiceDirection, from, to, page).invoiceDigest ?: emptyList())
        }
        return invoiceDigests
    }

    private suspend fun fetchInvoiceDigestPage(
        client: NavClient,
        navTechnicalUser: NavTechnicalUser,
        invoiceDirection: InvoiceDirection,
        from: Instant,
        to: Instant,
        page: Int) : QueryInvoiceDigestResponse.InvoiceDigestResult {
        return client
            .query(
                QueryInvoiceDigestRequest(
                    config =  createConfig(navTechnicalUser),
                    insDateFrom = from,
                    insDateTo = to,
                    page = page,
                    invoiceDirection = invoiceDirection,
                ),
                QueryInvoiceDigestResponse::class.java
            )
            .invoiceDigestResult
    }

    private suspend fun fetchInvoiceData(
        client: NavClient,
        navTechnicalUser: NavTechnicalUser,
        invoiceDigest: InvoiceDigest,
    ): InvoiceData? {
        val direction = calculateInvoiceDirection(invoiceDigest, navTechnicalUser.taxNumber)
        val invoiceDataXml = client.query(
            QueryInvoiceDataRequest(
                config =  createConfig(navTechnicalUser),
                invoiceNumber = invoiceDigest.invoiceNumber,
                invoiceDirection = direction,
                //supplierTaxNumber can only be filled for inbound invoices according to the NAV online invoice API docs
                supplierTaxNumber = if(direction == InvoiceDirection.INBOUND) invoiceDigest.supplierTaxNumber else null
            ),
            QueryInvoiceDataResponse::class.java
        ).invoiceDataXml!!

        return try {
            xmlMapper.readValue(invoiceDataXml, InvoiceData::class.java)
        } catch (e: MissingKotlinParameterException) {
            if(invoiceDataXml.contains("<batchInvoice>")) {
                log.warn(BATCH_INVOICE_SKIPPED_MESSAGE_TEMPLATE.format(invoiceDigest.invoiceNumber, invoiceDigest.supplierTaxNumber))
                return null
            } else {
                throw e
            }
        }
    }

    private fun createConfig(navTechnicalUser: NavTechnicalUser): Config {
        return Config(navTechnicalUser, librarySettings.consumerSoftwareInfo)
    }
}