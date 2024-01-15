package com.github.oliverszabo.navpolling.polling

import ch.qos.logback.classic.Level
import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.polling.dto.*
import com.github.oliverszabo.navpolling.testutil.LogCollector
import com.github.oliverszabo.navpolling.util.NavDataCreator
import com.github.oliverszabo.navpolling.util.minusDays
import com.github.oliverszabo.navpolling.util.plusDays
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat

//TODO: find a way to check whether the NavClient is created with the correct request timeout
class NavQueryServiceTest {
    private val to = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val logCollector = LogCollector.create(NavQueryService::class.java)

    private val inboundTechnicalUser = createTechnicalUser("inbound", setOf(InvoiceDirection.INBOUND), to.minusDays(36))
    private val outboundTechnicalUser = createTechnicalUser("outbound", setOf(InvoiceDirection.OUTBOUND), to.minusDays(38))
    private val bothDirectionsTechnicalUser = createTechnicalUser("bothDirections", setOf(InvoiceDirection.OUTBOUND, InvoiceDirection.INBOUND), to.minusDays(40))

    private val expectedDigestsForInboundTechnicalUser = listOf(
        createInvoiceDigest(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching1", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(5)),
        createInvoiceDigest(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching2", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(5)),
        createInvoiceDigest(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching3", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(5)),
        createInvoiceDigest(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching4", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusSeconds(1)),
        createInvoiceDigest(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching5", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusDays(1)),
    )
    private val expectedDigestsForOutboundTechnicalUser = listOf(
        createInvoiceDigest(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching1", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(10)),
        createInvoiceDigest(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching2", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(10)),
        createInvoiceDigest(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching3", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusDays(3)),
        createInvoiceDigest(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching4", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusSeconds(1)),
        createInvoiceDigest(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching5", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusDays(1)),
    )
    private val expectedDigestsForBothDirectionsTechnicalUser = listOf(
        createInvoiceDigest(bothDirectionsTechnicalUser, InvoiceDirection.OUTBOUND, "matching1", bothDirectionsTechnicalUser.pollingCompleteUntil!!.plusDays(10)),
        createInvoiceDigest(bothDirectionsTechnicalUser, InvoiceDirection.INBOUND, "matching2", bothDirectionsTechnicalUser.pollingCompleteUntil!!.plusDays(10)),
        // testing whether duplicates are properly removed
        createInvoiceDigest(bothDirectionsTechnicalUser, InvoiceDirection.OUTBOUND, "matching3", bothDirectionsTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST)),
        createInvoiceDigest(bothDirectionsTechnicalUser, InvoiceDirection.INBOUND, "matching4", bothDirectionsTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST)),
    )
    private val digests = listOf(
        // not matching invoices for inboundTechnicalUser
        createInvoiceDigest(inboundTechnicalUser, InvoiceDirection.OUTBOUND, "wrongDirection", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(1)),
        createInvoiceDigest(inboundTechnicalUser, InvoiceDirection.INBOUND, "insDateLessThanFrom", inboundTechnicalUser.pollingCompleteUntil!!.minusSeconds(1)),
        createInvoiceDigest(inboundTechnicalUser, InvoiceDirection.INBOUND, "insDateMoreThanTo", to.plusDays(1)),
        // not matching invoices for outboundTechnicalUser
        createInvoiceDigest(outboundTechnicalUser, InvoiceDirection.INBOUND, "wrongDirection", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(1)),
        createInvoiceDigest(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "insDateLessThanFrom", outboundTechnicalUser.pollingCompleteUntil!!.minusDays(1)),
        createInvoiceDigest(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "insDateMoreThanTo", to.plusSeconds(1)),
        // not matching invoices for bothDirectionsTechnicalUser
        createInvoiceDigest(bothDirectionsTechnicalUser, InvoiceDirection.OUTBOUND, "insDateLessThanFrom", bothDirectionsTechnicalUser.pollingCompleteUntil!!.minusSeconds(1)),
        createInvoiceDigest(bothDirectionsTechnicalUser, InvoiceDirection.INBOUND, "insDateEqualToTo", to),
    )
        .plus(expectedDigestsForInboundTechnicalUser)
        .plus(expectedDigestsForOutboundTechnicalUser)
        .plus(expectedDigestsForBothDirectionsTechnicalUser)

    private val librarySettings = mockk<LibrarySettings>(relaxed = true)
    private var navQueryService: NavQueryService? = null

    @BeforeEach
    fun beforeEach() {
        every { librarySettings.connectionPoolSize } returns 1
        every { librarySettings.requestTimeout } returns 1000

        navQueryService = NavQueryService(librarySettings)
    }

    @Test
    fun `fetchInvoiceDigests throws exception when pollingCompleteUntil is null or greater than to`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                navQueryService!!.fetchInvoiceDigests(
                    createTechnicalUser("t", emptySet(), null),
                    to
                )
            }
        }

        assertThrows<IllegalArgumentException> {
            runBlocking {
                navQueryService!!.fetchInvoiceDigests(
                    inboundTechnicalUser.withPollingCompleteUntil(to.plusSeconds(1)),
                    to
                )
            }
        }
    }

    @Test
    fun `fetchInvoiceDigests throws exception when pollingCompleteUntil or to is not truncated to seconds`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                navQueryService!!.fetchInvoiceDigests(
                    inboundTechnicalUser,
                    to.plusMillis(1)
                )
            }
        }

        assertThrows<IllegalArgumentException> {
            runBlocking {
                navQueryService!!.fetchInvoiceDigests(
                    inboundTechnicalUser.withPollingCompleteUntil(inboundTechnicalUser.pollingCompleteUntil!!.plusMillis(1)),
                    to
                )
            }
        }
    }

    @Test
    fun `fetchInvoiceDigests correctly fetches and filters data`() {
        mockNavClient()

        runBlocking {
            var fetchedDigests = navQueryService!!.fetchInvoiceDigests(inboundTechnicalUser, to)
            assertThat(fetchedDigests).containsExactlyInAnyOrderElementsOf(expectedDigestsForInboundTechnicalUser)

            fetchedDigests = navQueryService!!.fetchInvoiceDigests(outboundTechnicalUser, to)
            assertThat(fetchedDigests).containsExactlyInAnyOrderElementsOf(expectedDigestsForOutboundTechnicalUser)

            fetchedDigests = navQueryService!!.fetchInvoiceDigests(bothDirectionsTechnicalUser, to)
            assertThat(fetchedDigests).containsExactlyInAnyOrderElementsOf(expectedDigestsForBothDirectionsTechnicalUser)
        }
    }

    @Test
    fun `fetchInvoiceDigestsAndData correctly fetches and filters data`() {
        mockNavClient()

        runBlocking {
            var fetchedDigestsAndData = navQueryService!!.fetchInvoiceDigestsAndData(inboundTechnicalUser, to)
            assertInvoiceDigestsAndData(expectedDigestsForInboundTechnicalUser, fetchedDigestsAndData)

            fetchedDigestsAndData = navQueryService!!.fetchInvoiceDigestsAndData(outboundTechnicalUser, to)
            assertInvoiceDigestsAndData(expectedDigestsForOutboundTechnicalUser, fetchedDigestsAndData)

            fetchedDigestsAndData = navQueryService!!.fetchInvoiceDigestsAndData(bothDirectionsTechnicalUser, to)
            assertInvoiceDigestsAndData(expectedDigestsForBothDirectionsTechnicalUser, fetchedDigestsAndData)
        }
    }

    @Test
    fun `fetchInvoiceDigestsAndData skips and logs batchInvoices`() {
        mockNavClient { NavDataCreator.batchInvoiceData }

        runBlocking {
            var fetchedDigestsAndData = navQueryService!!.fetchInvoiceDigestsAndData(inboundTechnicalUser, to)
            assertEquals(0, fetchedDigestsAndData.size)
            assertBatchInvoiceLogs(expectedDigestsForInboundTechnicalUser)
            logCollector.reset()

            fetchedDigestsAndData = navQueryService!!.fetchInvoiceDigestsAndData(outboundTechnicalUser, to)
            assertEquals(0, fetchedDigestsAndData.size)
            assertBatchInvoiceLogs(expectedDigestsForOutboundTechnicalUser)
            logCollector.reset()

            fetchedDigestsAndData = navQueryService!!.fetchInvoiceDigestsAndData(bothDirectionsTechnicalUser, to)
            assertEquals(0, fetchedDigestsAndData.size)
            assertBatchInvoiceLogs(expectedDigestsForBothDirectionsTechnicalUser)
            logCollector.reset()
        }
    }

    private fun mockNavClient(invoiceDataSupplier: (QueryInvoiceDataRequest) -> String = this::defaultInvoiceDataSupplier) {
        mockkConstructor(NavClient::class)
        coEvery { anyConstructed<NavClient>().query<NavRequest, Any>(any(), any()) } answers {
            val request = firstArg<Any>()
            if(request is QueryInvoiceDigestRequest) {
                answerForDigestRequest(request)
            } else {
                answerForDataRequest(request as QueryInvoiceDataRequest, invoiceDataSupplier)
            }
        }
    }

    private fun answerForDigestRequest(request: QueryInvoiceDigestRequest): QueryInvoiceDigestResponse {
        val pageSize = 2

        val matchingDigests = digests
            .filter {
                if(request.invoiceDirection == InvoiceDirection.INBOUND) {
                    it.customerTaxNumber == request.config.user.taxNumber
                } else {
                    it.supplierTaxNumber == request.config.user.taxNumber
                }
            }
            .filter { it.insDate >= request.insDateFrom && it.insDate <= request.insDateTo }

        return if(matchingDigests.isEmpty()) {
            if(request.page != 1) {
                throw Exception("invalid page")
            }
            QueryInvoiceDigestResponse().apply {
                invoiceDigestResult = QueryInvoiceDigestResponse.InvoiceDigestResult(
                    invoiceDigest = emptyList(),
                    currentPage = 1,
                    availablePage = 1
                )
            }
        } else {
            val pagedMatchingDigests = matchingDigests.chunked(pageSize)
            QueryInvoiceDigestResponse().apply {
                invoiceDigestResult = QueryInvoiceDigestResponse.InvoiceDigestResult(
                    invoiceDigest = pagedMatchingDigests[request.page - 1],
                    currentPage = request.page,
                    availablePage = pagedMatchingDigests.size
                )
            }
        }
    }

    private fun answerForDataRequest(
        request: QueryInvoiceDataRequest,
        invoiceDataSupplier: (QueryInvoiceDataRequest) -> String
    ): QueryInvoiceDataResponse {
        return QueryInvoiceDataResponse(
            QueryInvoiceDataResponse.InvoiceDataResult(
                invoiceDataSupplier(request)
            )
        )
    }

    private fun defaultInvoiceDataSupplier(request: QueryInvoiceDataRequest): String {
        return NavDataCreator.createEncodedInvoiceData(
            request.invoiceNumber,
            request.supplierTaxNumber ?: request.config.user.taxNumber
        )
    }

    private fun createInvoiceDigest(
        technicalUser: TechnicalUser,
        invoiceDirection: InvoiceDirection,
        invoiceNumber: String,
        insDate: Instant
    ): InvoiceDigest {
        return InvoiceDigest(
            invoiceNumber = invoiceNumber,
            supplierTaxNumber = if(invoiceDirection == InvoiceDirection.INBOUND) "supplier" else technicalUser.taxNumber,
            insDate = insDate,
            invoiceCategory = "",
            invoiceIssueDate = LocalDate.now(),
            invoiceOperation = "",
            supplierName = "",
            customerTaxNumber = if(invoiceDirection == InvoiceDirection.INBOUND) technicalUser.taxNumber else "customer"
        )
    }

    private fun createTechnicalUser(taxNumber: String, pollingDirections: Set<InvoiceDirection>, pollingCompleteUntil: Instant?): TechnicalUser {
        return TechnicalUser(
            login = "${taxNumber}-login",
            passwordHash = "${taxNumber}-password",
            taxNumber = taxNumber,
            sigKey = "${taxNumber}-sigKey",
            pollingDirections = pollingDirections,
            pollingCompleteUntil = pollingCompleteUntil
        )
    }

    private fun assertInvoiceDigestsAndData(
        expectedInvoiceDigests: List<InvoiceDigest>,
        actualInvoiceDigestsAndData: List<Pair<InvoiceDigest, InvoiceData>>
    ) {
        assertThat(actualInvoiceDigestsAndData.map { it.first }).containsExactlyInAnyOrderElementsOf(expectedInvoiceDigests)
        assertThat(actualInvoiceDigestsAndData.map { it.second })
            .usingRecursiveFieldByFieldElementComparator()
            .containsExactlyInAnyOrderElementsOf(
                expectedInvoiceDigests.map { NavDataCreator.createInvoiceData(it.invoiceNumber, it.supplierTaxNumber) }
            )
    }

    private fun assertBatchInvoiceLogs(invoiceDigests: List<InvoiceDigest>) {
        invoiceDigests.forEach {
            assertTrue(
                logCollector.containsOnce(
                    NavQueryService.BATCH_INVOICE_SKIPPED_MESSAGE_TEMPLATE.format(it.invoiceNumber, it.supplierTaxNumber),
                    Level.WARN
                )
            )
        }
    }
}