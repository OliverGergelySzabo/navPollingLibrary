package com.github.oliverszabo.navpolling.polling

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.api.exception.NavInvoiceServiceConnectionException
import com.github.oliverszabo.navpolling.api.exception.NavQueryException
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisher
import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.createXmlMapper
import com.github.oliverszabo.navpolling.util.minusDays
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceFeedPollerTest {
    private val invoiceData = createXmlMapper().readValue(
        Paths.get("src","test","resources", "Peldaszamlak_v3.0", "Belfoldi devizas szamla.xml").toFile(),
        InvoiceData::class.java
    )
    private val invoiceDigest = InvoiceDigest(
        invoiceNumber = "invoiceNumber",
        invoiceOperation = "invoiceOperation",
        invoiceCategory = "invoiceCategory",
        invoiceIssueDate = LocalDate.now(),
        supplierTaxNumber = "supplierTaxNumber",
        supplierName = "supplierName",
        insDate = Instant.now(),
        batchIndex = BigInteger("10"),
        invoiceNetAmount = BigDecimal("10.9999999995")
    )

    private val invoiceFeed = mockk<InvoiceFeed>(relaxed = true)
    private val eventPublishers = IntRange(0, 1).map { mockk<EventPublisher>(relaxed = true) }
    private val navQueryService = mockk<NavQueryService>()
    private val logger = mockk<Logger>(relaxed = true)

    private val technicalUser = TechnicalUser("l", "p", "t", "s")
    private val newlyAddedTechnicalUser = TechnicalUser("n", "p", "t", "s")
    private val now = Instant.now()
    private val nowTruncated = now.truncatedTo(ChronoUnit.SECONDS)
    private val pollingCompleteUntil = now.minusDays(1)
    private val pollingCompleteUntilTruncated = pollingCompleteUntil.truncatedTo(ChronoUnit.SECONDS)

    @BeforeEach
    fun beforeEach() {
        mockkStatic(LoggerFactory::class)
        every { LoggerFactory.getLogger(InvoiceFeedPoller::class.java) } returns logger
        mockkStatic(Instant::class)
        every { Instant.now() } returns now

        every { invoiceFeed.isRunning() } returns true
        every { invoiceFeed.getUsers(any()) } returns emptySet()
        every { invoiceFeed.getPollingCompleteUntil() } returns pollingCompleteUntil
        every { invoiceFeed.getPastFetchingPeriod() } returns 0
        every { invoiceFeed.onPastFetchingCompleted(any()) } returns Unit
        every { invoiceFeed.setPollingCompleteUntil(any()) } returns Unit

        every { navQueryService.fetchInvoiceDigests(any(), any(), any()) } returns emptyList()
        every { navQueryService.fetchInvoiceDigestsAndData(any(), any(), any()) } returns emptyList()

        eventPublishers.forEach { eventPublisher ->
            every { eventPublisher.publishInvoiceArrivedEvent(any(), any(), any(), any()) } returns Unit
            every { eventPublisher.publishInvoiceArrivedEvent(any(), any(), any()) } returns Unit
            every { eventPublisher.isOnlyDigestDataRequired } returns true
        }
    }

    @Test
    fun whenTheFeedIsNotRunningThenNoPollingHappens() {
        val queryResult = listOf(
            NavQueryService.DigestQueryResult(invoiceDigest, technicalUser, InvoiceDirection.OUTBOUND),
            NavQueryService.DigestQueryResult(invoiceDigest, technicalUser, InvoiceDirection.INBOUND)
        )
        val pastFetchingQueryResult = listOf(
            NavQueryService.DigestQueryResult(invoiceDigest, newlyAddedTechnicalUser, InvoiceDirection.OUTBOUND),
            NavQueryService.DigestQueryResult(invoiceDigest, newlyAddedTechnicalUser, InvoiceDirection.INBOUND)
        )
        val pastFetchingPeriod = 15
        every { invoiceFeed.isRunning() } returns false
        every { invoiceFeed.getPastFetchingPeriod() } returns pastFetchingPeriod
        every { invoiceFeed.getUsers(false) } returns setOf(technicalUser)
        every { invoiceFeed.getUsers(true) } returns setOf(newlyAddedTechnicalUser)
        every { navQueryService.fetchInvoiceDigests(eq(setOf(technicalUser)), any(), any()) } returns queryResult
        every { navQueryService.fetchInvoiceDigests(eq(setOf(newlyAddedTechnicalUser)), any(), any()) } returns pastFetchingQueryResult

        createAndRunInvoiceFeedPoller()

        verify(exactly = 0) {
            navQueryService.fetchInvoiceDigestsAndData(any(), any(), any())
            navQueryService.fetchInvoiceDigests(any(), any(), any())
            invoiceFeed.onPastFetchingCompleted(any())
            invoiceFeed.setPollingCompleteUntil(any())
        }
        eventPublishers.forEach { publisher ->
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any(), any())
                publisher.publishInvoiceArrivedEvent(any(), any(), any())
            }
        }
    }

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Test
    fun whenNavInvoiceServiceConnectionExceptionOccursDuringPollingItIsLogged() {
        val cause = Exception("message")
        every { navQueryService.fetchInvoiceDigests(any(), any(), any()) } throws NavInvoiceServiceConnectionException(cause)

        createAndRunInvoiceFeedPoller()

        verify(exactly = 1) {
            logger.error(
                eq(InvoiceFeedPoller.NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE.format(cause::class.java.canonicalName, cause.message))
            )
        }
    }

    @Test
    fun whenNavQueryExceptionOccursDuringPollingItIsLogged() {
        val exception = NavQueryException("funcCode", "errorCode", "message")
        every { navQueryService.fetchInvoiceDigests(any(), any(), any()) } throws exception

        createAndRunInvoiceFeedPoller()

        verify(exactly = 1) {
            logger.error(
                eq(InvoiceFeedPoller.NAV_QUERY_ERROR_MESSAGE_TEMPLATE.format(exception.funcCode, exception.errorCode, exception.message))
            )
        }
    }

    @Test
    fun whenOnlyInvoiceDigestDataIsRequiredThenOnlyDigestsWillBeRequested() {
        val queryResult = listOf(
            NavQueryService.DigestQueryResult(invoiceDigest, technicalUser, InvoiceDirection.OUTBOUND),
            NavQueryService.DigestQueryResult(invoiceDigest, technicalUser, InvoiceDirection.INBOUND)
        )
        every { invoiceFeed.getUsers(false) } returns setOf(technicalUser)
        every { navQueryService.fetchInvoiceDigests(eq(setOf(technicalUser)), any(), any()) } returns queryResult

        createAndRunInvoiceFeedPoller()

        verify(exactly = 1) {
            navQueryService.fetchInvoiceDigests(eq(setOf(technicalUser)), eq(pollingCompleteUntilTruncated), eq(nowTruncated))
            invoiceFeed.onPastFetchingCompleted(emptySet())
            invoiceFeed.setPollingCompleteUntil(nowTruncated)
        }
        verify(exactly = 0) {
            navQueryService.fetchInvoiceDigestsAndData(any(), any(), any())
        }
        eventPublishers.forEach { publisher ->
            queryResult.forEach { result ->
                verify(exactly = 1) {
                    publisher.publishInvoiceArrivedEvent(eq(result.invoiceDigest), eq(result.technicalUser), eq(result.invoiceDirection))
                }
            }
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun whenInvoiceDataIsRequiredThenInvoiceDataWillBeRequested() {
        val queryResult = listOf(
            NavQueryService.QueryResult(invoiceData, invoiceDigest, technicalUser, InvoiceDirection.OUTBOUND),
            NavQueryService.QueryResult(invoiceData, invoiceDigest, technicalUser, InvoiceDirection.INBOUND)
        )
        every { eventPublishers[0].isOnlyDigestDataRequired } returns false
        every { invoiceFeed.getUsers(false) } returns setOf(technicalUser)
        every { navQueryService.fetchInvoiceDigestsAndData(eq(setOf(technicalUser)), any(), any()) } returns queryResult

        createAndRunInvoiceFeedPoller()

        verify(exactly = 1) {
            navQueryService.fetchInvoiceDigestsAndData(eq(setOf(technicalUser)), eq(pollingCompleteUntilTruncated), eq(nowTruncated))
            invoiceFeed.onPastFetchingCompleted(emptySet())
            invoiceFeed.setPollingCompleteUntil(nowTruncated)
        }
        verify(exactly = 0) {
            navQueryService.fetchInvoiceDigests(any(), any(), any())
        }
        eventPublishers.forEach { publisher ->
            queryResult.forEach { result ->
                verify(exactly = 1) {
                    publisher.publishInvoiceArrivedEvent(eq(result.invoiceData), eq(result.invoiceDigest), eq(result.technicalUser), eq(result.invoiceDirection))
                }
            }
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any())
            }
        }
    }

    @Test
    fun whenThereAreUsersWithPastFetchingRequirementThenForTheseUsersPastInvoicesWillBeFetchedAccordingToThePastFetchingPeriod() {
        val queryResult = listOf(
            NavQueryService.DigestQueryResult(invoiceDigest, technicalUser, InvoiceDirection.OUTBOUND),
            NavQueryService.DigestQueryResult(invoiceDigest, technicalUser, InvoiceDirection.INBOUND)
        )
        val pastFetchingQueryResult = listOf(
            NavQueryService.DigestQueryResult(invoiceDigest, newlyAddedTechnicalUser, InvoiceDirection.OUTBOUND),
            NavQueryService.DigestQueryResult(invoiceDigest, newlyAddedTechnicalUser, InvoiceDirection.INBOUND)
        )
        val pastFetchingPeriod = 15
        every { invoiceFeed.getPastFetchingPeriod() } returns pastFetchingPeriod
        every { invoiceFeed.getUsers(false) } returns setOf(technicalUser)
        every { invoiceFeed.getUsers(true) } returns setOf(newlyAddedTechnicalUser)
        every { navQueryService.fetchInvoiceDigests(eq(setOf(technicalUser)), any(), any()) } returns queryResult
        every { navQueryService.fetchInvoiceDigests(eq(setOf(newlyAddedTechnicalUser)), any(), any()) } returns pastFetchingQueryResult

        createAndRunInvoiceFeedPoller()

        //for some reason this method call yields an incorrect result inside the verify scope
        val expectedPastFetchingFrom = nowTruncated.minusDays(pastFetchingPeriod)
        verify(exactly = 1) {
            navQueryService.fetchInvoiceDigests(eq(setOf(technicalUser)), eq(pollingCompleteUntilTruncated), eq(nowTruncated))
            navQueryService.fetchInvoiceDigests(eq(setOf(newlyAddedTechnicalUser)), eq(expectedPastFetchingFrom), eq(nowTruncated))
            invoiceFeed.onPastFetchingCompleted(setOf(newlyAddedTechnicalUser))
            invoiceFeed.setPollingCompleteUntil(nowTruncated)
        }
        verify(exactly = 0) {
            navQueryService.fetchInvoiceDigestsAndData(any(), any(), any())
        }
        eventPublishers.forEach { publisher ->
            queryResult.forEach { result ->
                verify(exactly = 1) {
                    publisher.publishInvoiceArrivedEvent(eq(result.invoiceDigest), eq(result.technicalUser), eq(result.invoiceDirection))
                }
            }
            pastFetchingQueryResult.forEach { result ->
                verify(exactly = 1) {
                    publisher.publishInvoiceArrivedEvent(eq(result.invoiceDigest), eq(result.technicalUser), eq(result.invoiceDirection))
                }
            }
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any(), any())
            }
        }
    }

    private fun createAndRunInvoiceFeedPoller(): InvoiceFeedPoller {
        return InvoiceFeedPoller(invoiceFeed, eventPublishers, navQueryService).apply { run() }
    }
}