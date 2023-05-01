package com.github.oliverszabo.navpolling.communication

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.communication.dto.*
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.isTruncatedTo
import com.github.oliverszabo.navpolling.util.plusDays
import kotlinx.coroutines.*
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import javax.annotation.PreDestroy

@Component
class NavQueryService(
    librarySettings: LibrarySettings
) {
    companion object {
        const val MAX_NUMBER_OF_DAYS_IN_REQUEST = 34L
    }

    private val connectionScope = CoroutineScope(
        SupervisorJob() + Executors.newFixedThreadPool(librarySettings.connectionPoolSize).asCoroutineDispatcher()
    )

    @PreDestroy
    private fun destroy() {
        connectionScope.cancel()
    }

    fun fetchInvoiceDigestsAndData(
        technicalUsers: Set<TechnicalUser>,
        from: Instant,
        to: Instant
    ): List<QueryResult> {
        return runBlocking {
            //todo: make this optionally obey nav rate limiting rules
            val client = NavClient()
            return@runBlocking fetchInvoiceDigests(technicalUsers, from, to).map { (invoiceDigest, technicalUser, direction) ->
                connectionScope.async {
                    QueryResult(
                        fetchInvoiceData(client, NavTechnicalUser.from(technicalUser), invoiceDigest, direction),
                        invoiceDigest,
                        technicalUser,
                        direction
                    )
                }
            }.awaitAll()
        }
    }

    fun fetchInvoiceDigests(
        technicalUsers: Set<TechnicalUser>,
        from: Instant,
        to: Instant
    ): List<DigestQueryResult> {
        if(from > to) {
            throw IllegalArgumentException("The 'from' param cannot be after the 'to' param")
        }
        if(!from.isTruncatedTo(ChronoUnit.SECONDS) || !to.isTruncatedTo(ChronoUnit.SECONDS)) {
            throw IllegalArgumentException("The 'from' and 'to' params must be truncated to seconds")
        }

        val client = NavClient()
        val bounds = mutableListOf<Pair<Instant, Instant>>()
        var currentBoundFrom = from
        do {
            val currentBoundTo = minOf(to, currentBoundFrom.plusDays(MAX_NUMBER_OF_DAYS_IN_REQUEST))
            bounds.add(Pair(currentBoundFrom, currentBoundTo))
            currentBoundFrom = currentBoundTo
        } while (to > currentBoundTo)

        return runBlocking {
            return@runBlocking technicalUsers.flatMap { technicalUser ->
                bounds.flatMap { (from, to) ->
                    technicalUser.pollingDirections.map { direction ->
                        connectionScope.async {
                            fetchInvoiceDigestsForPeriod(client, NavTechnicalUser.from(technicalUser), direction, from, to).map {
                                DigestQueryResult(it, technicalUser, direction)
                            }
                        }
                    }
                }
            }
                .awaitAll()
                .flatten()
                // removing invoices on the upper bound, as this function has an exclusive upper bound
                // while the NAV API has an inclusive upper bound
                //todo: decide whether additional measures are needed
                // (i.e. extend invoice state with potential duplicates and make this functions 'to' param inclusive
                .filter { (digest, _, _) -> digest.insDate != to }
                // removing potential duplicates caused by overlapping boundaries
                .distinctBy { (digest, _, _) -> digest }
        }
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
        invoiceDigests.addAll(firstPage.invoiceDigest ?: emptyArray())
        val numberOfPages = firstPage.availablePage ?: 0
        for(page in 2..numberOfPages) {
            invoiceDigests.addAll(fetchInvoiceDigestPage(client, navTechnicalUser, invoiceDirection, from, to, page).invoiceDigest ?: emptyArray())
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
        invoiceDirection: InvoiceDirection): InvoiceData {

        return client.query(
            QueryInvoiceDataRequest(
                config =  createConfig(navTechnicalUser),
                invoiceNumber = invoiceDigest.invoiceNumber,
                invoiceDirection = invoiceDirection,
                //supplierTaxNumber can only be filled for inbound invoices according to the NAV online invoice API docs
                supplierTaxNumber = if(invoiceDirection == InvoiceDirection.INBOUND) invoiceDigest.supplierTaxNumber else null
            ),
            QueryInvoiceDataResponse::class.java
        ).invoiceData!!
    }

    private fun createConfig(navTechnicalUser: NavTechnicalUser): Config {
        return Config(
            navTechnicalUser,
            //todo: this has to come from config
            Software(
                softwareId = "HU27917882-3241244",
                softwareName = "Nav polling library",
                softwareOperation = Software.SoftwareOperation.LOCAL_SOFTWARE,
                softwareMainVersion = "0.1",
                softwareDevName = "Gergely Olivér Szabó",
                softwareDevContact = "szabogergelyoliver@gmail.com",
                softwareDevCountryCode = "HU",
            )
        )
    }

    data class QueryResult(
        val invoiceData: InvoiceData,
        val invoiceDigest: InvoiceDigest,
        val technicalUser: TechnicalUser,
        val invoiceDirection: InvoiceDirection
    )

    data class DigestQueryResult(
        val invoiceDigest: InvoiceDigest,
        val technicalUser: TechnicalUser,
        val invoiceDirection: InvoiceDirection
    )
}