package com.github.oliverszabo.navpolling.communication

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.communication.dto.NavTechnicalUser
import com.github.oliverszabo.navpolling.communication.dto.QueryInvoiceDigestRequest
import com.github.oliverszabo.navpolling.communication.dto.QueryInvoiceDigestResponse
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.assertListsContainSameElements
import com.github.oliverszabo.navpolling.util.minusDays
import com.github.oliverszabo.navpolling.util.plusDays
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class NavQueryServiceTest {
    private val inboundTechnicalUser = createTechnicalUser("inbound", setOf(InvoiceDirection.INBOUND))
    private val outboundTechnicalUser = createTechnicalUser("outbound", setOf(InvoiceDirection.OUTBOUND))
    private val bothDirectionsTechnicalUser = createTechnicalUser("bothDirections", setOf(InvoiceDirection.OUTBOUND, InvoiceDirection.INBOUND))

    private val from = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val to = from.plusDays(50)

    private val expectedMatchingDigestData = listOf(
        // matching invoices for inboundTechnicalUser
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching1", "inboundSupplier", from.plusDays(5)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching2", "inboundSupplier", from.plusDays(5)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching3", "inboundSupplier", from.plusDays(5)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching4", "inboundSupplier", from.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusSeconds(1)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching5", "inboundSupplier", from.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusDays(1)),
        // matching invoices for outboundTechnicalUser
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching1", "outbound", from.plusDays(10)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching2", "outbound", from.plusDays(10)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching3", "outbound", from.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusDays(1)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching4", "outbound", from.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusSeconds(1)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching5", "outbound", from.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusDays(1)),
        // matching invoices for bothDirectionsTechnicalUser
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.OUTBOUND, "matching1", "bothDirections", from.plusDays(10)),
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.INBOUND, "matching2", "bothDirectionsSupplier", from.plusDays(10)),
        // testing whether duplicates are properly removed
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.OUTBOUND, "matching3", "bothDirections", from.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST)),
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.INBOUND, "matching4", "bothDirectionsSupplier", from.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST)),
    )
    private val digestData = listOf(
        // not matching invoices for inboundTechnicalUser
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.OUTBOUND, "wrongDirection", "inboundSupplier", from.plusDays(1)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "insDateLessThanFrom", "inboundSupplier", from.minusSeconds(1)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "insDateMoreThanTo", "inboundSupplier", to.plusDays(1)),
        // not matching invoices for outboundTechnicalUser
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.INBOUND, "wrongDirection", "outbound", from.plusDays(1)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "insDateLessThanFrom", "outbound", from.minusDays(1)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "insDateMoreThanTo", "outbound", to.plusSeconds(1)),
        // not matching invoices for bothDirectionsTechnicalUser
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.OUTBOUND, "insDateLessThanFrom", "bothDirections", from.minusSeconds(1)),
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.INBOUND, "insDateEqualToTo", "bothDirectionsSupplier", to),
    ).plus(expectedMatchingDigestData)

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
                    it.first == request.config.user.taxNumber
                            && it.second == request.invoiceDirection
                            && it.third.insDate >= request.insDateFrom
                            && it.third.insDate <= request.insDateTo
                }
                .map { it.third }

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

        navQueryService = NavQueryService(librarySettings)
    }

    @Test
    fun whenFromIsGreaterThanToFetchInvoiceDigestThrowsException() {
        assertThrows<IllegalArgumentException> {
            navQueryService!!.fetchInvoiceDigests(emptySet(), from, from.minusDays(1))
        }
    }

    @Test
    fun whenFromOrToIsNotTruncatedToSecondsInvoiceDigestThrowsException() {
        assertThrows<IllegalArgumentException> {
            navQueryService!!.fetchInvoiceDigests(emptySet(), from, to.plusMillis(1))
        }

        assertThrows<IllegalArgumentException> {
            navQueryService!!.fetchInvoiceDigests(emptySet(), from.plusMillis(1), to)
        }

        assertThrows<IllegalArgumentException> {
            navQueryService!!.fetchInvoiceDigests(emptySet(), from.plusMillis(1), to.plusMillis(1))
        }
    }

    @Test
    fun whenCorrectParamsSuppliedFetchInvoiceDigestReturnsExpectedResult() {
        val fetchedDigests = navQueryService!!.fetchInvoiceDigests(
            setOf(inboundTechnicalUser, outboundTechnicalUser, bothDirectionsTechnicalUser),
            from,
            to
        ).toMutableList()

        assertListsContainSameElements(expectedMatchingDigestData, fetchedDigests) {
            (expectedTechnicalUserTaxNumber, expectedDirection, expectedDigest), (actualDigest, actualTechnicalUser, actualDirection) ->
            expectedDigest == actualDigest && expectedDirection == actualDirection && expectedTechnicalUserTaxNumber == actualTechnicalUser.taxNumber
        }
    }

    private fun createInvoiceDigestData(
        technicalUser: TechnicalUser,
        invoiceDirection: InvoiceDirection,
        invoiceNumber: String,
        supplierTaxNumber: String,
        insDate: Instant
    ): Triple<String, InvoiceDirection, InvoiceDigest> {
        return Triple(
            technicalUser.taxNumber,
            invoiceDirection,
            InvoiceDigest(
                invoiceNumber = invoiceNumber,
                supplierTaxNumber = supplierTaxNumber,
                insDate = insDate,
                invoiceCategory = "",
                invoiceIssueDate = LocalDate.now(),
                invoiceOperation = "",
                supplierName = ""
            )
        )
    }

    private fun createTechnicalUser(taxNumber: String, pollingDirections: Set<InvoiceDirection>): TechnicalUser {
        return TechnicalUser(
            login = "${taxNumber}-login",
            password = "${taxNumber}-password",
            taxNumber = taxNumber,
            sigKey = "${taxNumber}-sigKey",
            pollingDirections = pollingDirections
        )
    }
}