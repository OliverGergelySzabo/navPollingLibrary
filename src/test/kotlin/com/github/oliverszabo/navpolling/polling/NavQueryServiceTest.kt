package com.github.oliverszabo.navpolling.polling

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.polling.dto.QueryInvoiceDigestRequest
import com.github.oliverszabo.navpolling.polling.dto.QueryInvoiceDigestResponse
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.polling.dto.NavTechnicalUser
import com.github.oliverszabo.navpolling.util.assertListsContainSameElements
import com.github.oliverszabo.navpolling.util.minusDays
import com.github.oliverszabo.navpolling.util.plusDays
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.stream.Stream

//todo: find a way to check whether the NavClient is created with the correct request timeout
class NavQueryServiceTest {
    private val to = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    private val inboundTechnicalUser = createTechnicalUser("inbound", setOf(InvoiceDirection.INBOUND), to.minusDays(36))
    private val outboundTechnicalUser = createTechnicalUser("outbound", setOf(InvoiceDirection.OUTBOUND), to.minusDays(38))
    private val bothDirectionsTechnicalUser = createTechnicalUser("bothDirections", setOf(InvoiceDirection.OUTBOUND, InvoiceDirection.INBOUND), to.minusDays(40))

    private val expectedMatchingDigestData = listOf(
        // matching invoices for inboundTechnicalUser
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching1", "inboundSupplier", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(5)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching2", "inboundSupplier", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(5)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching3", "inboundSupplier", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(5)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching4", "inboundSupplier", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusSeconds(1)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "matching5", "inboundSupplier", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusDays(1)),
        // matching invoices for outboundTechnicalUser
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching1", "outbound", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(10)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching2", "outbound", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(10)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching3", "outbound", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusDays(3)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching4", "outbound", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusSeconds(1)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "matching5", "outbound", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST).plusDays(1)),
        // matching invoices for bothDirectionsTechnicalUser
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.OUTBOUND, "matching1", "bothDirections", bothDirectionsTechnicalUser.pollingCompleteUntil!!.plusDays(10)),
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.INBOUND, "matching2", "bothDirectionsSupplier", bothDirectionsTechnicalUser.pollingCompleteUntil!!.plusDays(10)),
        // testing whether duplicates are properly removed
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.OUTBOUND, "matching3", "bothDirections", bothDirectionsTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST)),
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.INBOUND, "matching4", "bothDirectionsSupplier", bothDirectionsTechnicalUser.pollingCompleteUntil!!.plusDays(NavQueryService.MAX_NUMBER_OF_DAYS_IN_REQUEST)),
    )
    private val digestData = listOf(
        // not matching invoices for inboundTechnicalUser
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.OUTBOUND, "wrongDirection", "inboundSupplier", inboundTechnicalUser.pollingCompleteUntil!!.plusDays(1)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "insDateLessThanFrom", "inboundSupplier", inboundTechnicalUser.pollingCompleteUntil!!.minusSeconds(1)),
        createInvoiceDigestData(inboundTechnicalUser, InvoiceDirection.INBOUND, "insDateMoreThanTo", "inboundSupplier", to.plusDays(1)),
        // not matching invoices for outboundTechnicalUser
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.INBOUND, "wrongDirection", "outbound", outboundTechnicalUser.pollingCompleteUntil!!.plusDays(1)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "insDateLessThanFrom", "outbound", outboundTechnicalUser.pollingCompleteUntil!!.minusDays(1)),
        createInvoiceDigestData(outboundTechnicalUser, InvoiceDirection.OUTBOUND, "insDateMoreThanTo", "outbound", to.plusSeconds(1)),
        // not matching invoices for bothDirectionsTechnicalUser
        createInvoiceDigestData(bothDirectionsTechnicalUser, InvoiceDirection.OUTBOUND, "insDateLessThanFrom", "bothDirections", bothDirectionsTechnicalUser.pollingCompleteUntil!!.minusSeconds(1)),
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
    fun whenFromIsGreaterThanToFetchInvoiceDigestThrowsException() {
        assertThrows<IllegalArgumentException> {
            navQueryService!!.fetchInvoiceDigests(
                setOf(
                    inboundTechnicalUser.withPollingCompleteUntil(to.plusSeconds(1)),
                    outboundTechnicalUser,
                    bothDirectionsTechnicalUser),
                to
            )
        }
    }

    @Test
    fun whenFromOrToIsNotTruncatedToSecondsInvoiceDigestThrowsException() {
        assertThrows<IllegalArgumentException> {
            navQueryService!!.fetchInvoiceDigests(
                setOf(inboundTechnicalUser, outboundTechnicalUser, bothDirectionsTechnicalUser),
                to.plusMillis(1)
            )
        }

        assertThrows<IllegalArgumentException> {
            navQueryService!!.fetchInvoiceDigests(
                setOf(
                    inboundTechnicalUser.withPollingCompleteUntil(inboundTechnicalUser.pollingCompleteUntil!!.plusMillis(1)),
                    outboundTechnicalUser,
                    bothDirectionsTechnicalUser
                ),
                to
            )
        }
    }

    //parametrized test is needed to test both cases for NavTechnicalUser.from call correctness
    //todo: find better approach for this
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun whenCorrectParamsSuppliedFetchInvoiceDigestReturnsExpectedResult(passwordHashingRequired: Boolean) {
        every { librarySettings.passwordHashingRequired } returns passwordHashingRequired

        val fetchedDigests = navQueryService!!.fetchInvoiceDigests(
            setOf(inboundTechnicalUser, outboundTechnicalUser, bothDirectionsTechnicalUser),
            to
        ).toMutableList()

        assertListsContainSameElements(expectedMatchingDigestData, fetchedDigests) {
            (expectedTechnicalUserTaxNumber, expectedDirection, expectedDigest), (actualDigest, actualTechnicalUser, actualDirection) ->
            expectedDigest == actualDigest && expectedDirection == actualDirection && expectedTechnicalUserTaxNumber == actualTechnicalUser.taxNumber
        }
    }

    //todo: test for InvoiceData fetching

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