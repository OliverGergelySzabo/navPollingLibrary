package com.github.oliverszabo.navpolling.polling

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.polling.dto.QueryInvoiceDigestRequest
import com.github.oliverszabo.navpolling.polling.dto.QueryInvoiceDigestResponse
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.polling.dto.NavTechnicalUser
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

//todo: find a way to check whether the NavClient is created with the correct request timeout
class NavQueryServiceTest {
    private val to = Instant.now().truncatedTo(ChronoUnit.SECONDS)

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
    private val digestData = listOf(
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
        mockkConstructor(NavClient::class)
        coEvery { anyConstructed<NavClient>().query<QueryInvoiceDigestRequest, QueryInvoiceDigestResponse>(any(), any()) } answers {
            val request = firstArg<QueryInvoiceDigestRequest>()
            val pageSize = 2

            val matchingDigests = digestData
                .filter {
                    if(request.invoiceDirection == InvoiceDirection.INBOUND) {
                        it.customerTaxNumber == request.config.user.taxNumber
                    } else {
                        it.supplierTaxNumber == request.config.user.taxNumber
                    }
                }
                .filter { it.insDate >= request.insDateFrom && it.insDate <= request.insDateTo }

            if(matchingDigests.isEmpty()) {
                if(request.page != 1) {
                    throw Exception("invalid page")
                }
                QueryInvoiceDigestResponse().apply {
                    invoiceDigestResult = QueryInvoiceDigestResponse.InvoiceDigestResult().apply {
                        invoiceDigest = emptyArray()
                        currentPage = 1
                        availablePage = 1
                    }
                }
            } else {
                val pagedMatchingDigests = matchingDigests.chunked(pageSize)
                QueryInvoiceDigestResponse().apply {
                    invoiceDigestResult = QueryInvoiceDigestResponse.InvoiceDigestResult().apply {
                        invoiceDigest = pagedMatchingDigests[request.page - 1].toTypedArray()
                        currentPage = request.page
                        availablePage = pagedMatchingDigests.size
                    }
                }
            }
        }

        every { librarySettings.connectionPoolSize } returns 1
        every { librarySettings.requestTimeout } returns 1000

        mockkObject(NavTechnicalUser.Companion)
        every { NavTechnicalUser.from(any(), any()) } answers {
            // assertion for the fact that the NavQueryService call the NavTechnicalUser.from
            // with the correct value form library settings
            assertEquals(librarySettings.passwordHashingRequired, secondArg())
            callOriginal()
        }

        navQueryService = NavQueryService(librarySettings)
    }

    @Test
    fun `fetchInvoiceDigests throws exception when pollingCompleteUntil is greater than to`() {
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

    //parametrized test is needed to test both cases for NavTechnicalUser.from call correctness
    //todo: find better approach for this
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `fetchInvoiceDigests correctly fetches and filters data`(passwordHashingRequired: Boolean) {
        every { librarySettings.passwordHashingRequired } returns passwordHashingRequired

        runBlocking {
            var fetchedDigests = navQueryService!!.fetchInvoiceDigests(inboundTechnicalUser, to)
            assertThat(fetchedDigests).containsExactlyInAnyOrderElementsOf(expectedDigestsForInboundTechnicalUser)

            fetchedDigests = navQueryService!!.fetchInvoiceDigests(outboundTechnicalUser, to)
            assertThat(fetchedDigests).containsExactlyInAnyOrderElementsOf(expectedDigestsForOutboundTechnicalUser)

            fetchedDigests = navQueryService!!.fetchInvoiceDigests(bothDirectionsTechnicalUser, to)
            assertThat(fetchedDigests).containsExactlyInAnyOrderElementsOf(expectedDigestsForBothDirectionsTechnicalUser)
        }
    }

    //todo: test for InvoiceData fetching

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

    private fun createTechnicalUser(taxNumber: String, pollingDirections: Set<InvoiceDirection>, pollingCompleteUntil: Instant): TechnicalUser {
        return TechnicalUser(
            login = "${taxNumber}-login",
            password = "${taxNumber}-password",
            taxNumber = taxNumber,
            sigKey = "${taxNumber}-sigKey",
            pollingDirections = pollingDirections,
            pollingCompleteUntil = pollingCompleteUntil
        )
    }
}