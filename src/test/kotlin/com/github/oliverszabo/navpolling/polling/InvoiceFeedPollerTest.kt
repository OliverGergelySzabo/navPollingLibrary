package com.github.oliverszabo.navpolling.polling

import ch.qos.logback.classic.Level
import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.exception.ErrorOccurredInEventHandlerException
import com.github.oliverszabo.navpolling.api.exception.InvoiceMappingException
import com.github.oliverszabo.navpolling.api.exception.NavInvoiceServiceConnectionException
import com.github.oliverszabo.navpolling.api.exception.NavQueryException
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisher
import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.testutil.LogCollector
import com.github.oliverszabo.navpolling.util.CurrentTimeProvider
import com.github.oliverszabo.navpolling.util.createTechnicalUser
import com.github.oliverszabo.navpolling.util.createXmlMapper
import io.mockk.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceFeedPollerTest {
    private val now = Instant.now()
    private val nowTruncated = now.truncatedTo(ChronoUnit.SECONDS)
    private val technicalUser = createTechnicalUser("l", nowTruncated)
    private val otherTechnicalUser = createTechnicalUser("ol", nowTruncated)
    private val hasNoInvoiceTechnicalUser = createTechnicalUser("hl", nowTruncated)
    private val allUsers = setOf(technicalUser, otherTechnicalUser, hasNoInvoiceTechnicalUser)

    private val invoiceData = createXmlMapper().readValue(
        Paths.get("src","test","resources", "Peldaszamlak_v3.0", "Belfoldi devizas szamla.xml").toFile(),
        InvoiceData::class.java
    )
    private val inboundInvoiceDigest = createInvoiceDigest("in1", "supplierTaxNumber")
    private val otherInboundInvoiceDigest = createInvoiceDigest("in2", "supplierTaxNumber")
    private val outBoundInvoiceDigest = createInvoiceDigest("in3", technicalUser.taxNumber)

    private val invoiceFeed = mockk<InvoiceFeed>(relaxed = true)
    private val eventPublishers = IntRange(0, 1).map { mockk<EventPublisher>(relaxed = true) }
    private val navQueryService = mockk<NavQueryService>()
    private val logCollector = LogCollector.create(InvoiceFeedPoller::class.java)
    private val currentTimeProvider = mockk<CurrentTimeProvider>(relaxed = true)
    private val connectionScope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    @BeforeEach
    fun beforeEach() {
        every { currentTimeProvider.currentSecond() } returns nowTruncated

        every { invoiceFeed.isRunning() } returns true
        every { invoiceFeed.getUsers() } returns emptySet()
        every { invoiceFeed.getPastFetchingPeriod() } returns 0
        every { invoiceFeed.saveUsers() } returns Unit

        coEvery { navQueryService.fetchInvoiceDigests(any(), any()) } returns emptyList()
        coEvery { navQueryService.fetchInvoiceDigestsAndData(any(), any()) } returns emptyList()

        eventPublishers.forEach { eventPublisher ->
            every { eventPublisher.publishInvoiceArrivedEvent(any(), any(), any(), any()) } returns Unit
            every { eventPublisher.publishInvoiceArrivedEvent(any(), any(), any()) } returns Unit
            every { eventPublisher.isOnlyDigestDataRequired } returns true
        }
    }

    @AfterEach
    fun afterEach() {
        clearAllMocks()
        logCollector.reset()
    }

    @Test
    fun `when the invoiceFeed is stopped then polling is not initiated`() {
        val pastFetchingPeriod = 15
        every { invoiceFeed.isRunning() } returns false
        every { invoiceFeed.getPastFetchingPeriod() } returns pastFetchingPeriod
        every { invoiceFeed.getUsers() } returns setOf(technicalUser)

        createAndRunInvoiceFeedPoller(true)

        coVerify(exactly = 0) {
            navQueryService.fetchInvoiceDigestsAndData(any(), any())
            navQueryService.fetchInvoiceDigests(any(), any())
        }
        verify(exactly = 0) {
            invoiceFeed.compareAndSetPollingCompleteUntilForUsers(any(), any())
        }
        eventPublishers.forEach { publisher ->
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any(), any())
                publisher.publishInvoiceArrivedEvent(any(), any(), any())
            }
        }
        //if no polling happens then save should never be called (as there is no change is user state)
        verifySaveUsersIsNotCalled()
    }

    @Test
    fun `when NavInvoiceServiceConnectionException occurs polling is interrupted and the exception is logged`() {
        val cause = Exception("message")
        coEvery { navQueryService.fetchInvoiceDigests(any(), any()) } throws NavInvoiceServiceConnectionException(cause)
        every { invoiceFeed.getUsers() } returns setOf(technicalUser)

        createAndRunInvoiceFeedPoller(true)

        // for some reason in test the thrown NavInvoiceServiceConnectionException is wrapped into another NavInvoiceServiceConnectionException
        // most probably by the coroutine internals due to this the content of the error cannot be asserted
        // expected content: InvoiceFeedPoller.NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE.format(cause::class.java.canonicalName, cause.message)
        // TODO: find out why
        assertEquals(1, logCollector.countOfLoggedEvents(Level.ERROR))
        //interruption of polling means that no events are dispatched
        eventPublishers.forEach { publisher ->
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any(), any())
                publisher.publishInvoiceArrivedEvent(any(), any(), any())
            }
        }
        //if polling is interrupted (and saveUsersAfterPolling is true) then no save should happen
        verifySaveUsersIsNotCalled()
    }

    @Test
    fun `when the polling thread is interrupted polling is interrupted and this is logged`() {
        coEvery { navQueryService.fetchInvoiceDigests(any(), any()) } throws InterruptedException()
        every { invoiceFeed.getUsers() } returns setOf(technicalUser)

        createAndRunInvoiceFeedPoller(true)

        assertTrue(
            logCollector.containsOnce(
                InvoiceFeedPoller.POLLING_INTERRUPTED_MESSAGE_TEMPLATE.format(invoiceFeed::class.java.canonicalName),
                Level.WARN
            )
        )
        //interruption of polling means that no events are dispatched
        eventPublishers.forEach { publisher ->
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any(), any())
                publisher.publishInvoiceArrivedEvent(any(), any(), any())
            }
        }
        //if polling is interrupted (and saveUsersAfterPolling is true) then no save should happen
        verifySaveUsersIsNotCalled()
    }

    @Test
    fun `when NavQueryException occurs, polling is not interrupted and the exception is logged (invoiceDigest case)`() {
        val exception = NavQueryException("funcCode", "errorCode", "message")
        coEvery { navQueryService.fetchInvoiceDigests(technicalUser, any()) } throws exception
        coEvery { navQueryService.fetchInvoiceDigests(otherTechnicalUser, any()) } returns  listOf(inboundInvoiceDigest)
        every { invoiceFeed.getUsers() } returns setOf(technicalUser, otherTechnicalUser)

        createAndRunInvoiceFeedPoller()

        verify(exactly = 1) {
            //pollingCompleteUntil is only updated for the user without error
            invoiceFeed.compareAndSetPollingCompleteUntilForUsers(
                eq(setOf(otherTechnicalUser)),
                eq(nowTruncated)
            )
        }
        assertTrue(
            logCollector.containsOnce(
                InvoiceFeedPoller.NAV_QUERY_ERROR_MESSAGE_TEMPLATE.format(
                    technicalUser.login,
                    technicalUser.taxNumber,
                    exception.funcCode,
                    exception.errorCode,
                    exception.message
                ),
                Level.ERROR
            )
        )
        eventPublishers.forEach { publisher ->
            verify(exactly = 1) {
                publisher.publishInvoiceArrivedEvent(eq(inboundInvoiceDigest), eq(otherTechnicalUser), eq(InvoiceDirection.INBOUND))
            }
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any(), any())
            }
        }
        verifySaveUsersIsNotCalled()
    }

    @Test
    fun `when NavQueryException occurs, polling is not interrupted and the exception is logged (invoiceData case)`() {
        val exception = NavQueryException("funcCode", "errorCode", "message")
        coEvery { navQueryService.fetchInvoiceDigestsAndData(technicalUser, any()) } throws exception
        coEvery { navQueryService.fetchInvoiceDigestsAndData(otherTechnicalUser, any()) } returns  listOf(Pair(inboundInvoiceDigest, invoiceData))
        every { invoiceFeed.getUsers() } returns setOf(technicalUser, otherTechnicalUser)
        every { eventPublishers[0].isOnlyDigestDataRequired } returns false

        createAndRunInvoiceFeedPoller(true)

        verify(exactly = 1) {
            // pollingCompleteUntil is only updated for the user without error
            invoiceFeed.compareAndSetPollingCompleteUntilForUsers(
                eq(setOf(otherTechnicalUser)),
                eq(nowTruncated)
            )
        }
        assertTrue(
            logCollector.containsOnce(
                InvoiceFeedPoller.NAV_QUERY_ERROR_MESSAGE_TEMPLATE.format(
                    technicalUser.login,
                    technicalUser.taxNumber,
                    exception.funcCode,
                    exception.errorCode,
                    exception.message
                ),
                Level.ERROR
            )
        )
        eventPublishers.forEach { publisher ->
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any())
            }
            verify(exactly = 1) {
                publisher.publishInvoiceArrivedEvent(
                    eq(inboundInvoiceDigest),
                    eq(invoiceData),
                    eq(otherTechnicalUser),
                    eq(InvoiceDirection.INBOUND)
                )
            }
        }
        //if a non-interrupting exception occurs (and saveUsersAfterPolling is true) then save is called
        verifySaveUsersIsCalled()
    }

    @Test
    fun `invoice data is correctly propagated to event publishers (invoiceDigest case)`() {
        coEvery { navQueryService.fetchInvoiceDigests(technicalUser, any()) } returns listOf(outBoundInvoiceDigest)
        coEvery { navQueryService.fetchInvoiceDigests(otherTechnicalUser, any()) } returns listOf(inboundInvoiceDigest, otherInboundInvoiceDigest)
        every { invoiceFeed.getUsers() } returns allUsers

        createAndRunInvoiceFeedPoller()

        allUsers.forEach { user ->
            coVerify(exactly = 1) {
                navQueryService.fetchInvoiceDigests(eq(user), eq(nowTruncated))
            }
        }
        coVerify(exactly = 0) {
            navQueryService.fetchInvoiceDigestsAndData(any(), any())
        }

        verify(exactly = 1) {
            invoiceFeed.compareAndSetPollingCompleteUntilForUsers(
                eq(setOf(technicalUser, otherTechnicalUser, hasNoInvoiceTechnicalUser)),
                eq(nowTruncated)
            )
        }
        eventPublishers.forEach { publisher ->
            verify(exactly = 1) {
                publisher.publishInvoiceArrivedEvent(outBoundInvoiceDigest, technicalUser, InvoiceDirection.OUTBOUND)
                publisher.publishInvoiceArrivedEvent(inboundInvoiceDigest, otherTechnicalUser, InvoiceDirection.INBOUND)
                publisher.publishInvoiceArrivedEvent(otherInboundInvoiceDigest, otherTechnicalUser, InvoiceDirection.INBOUND)
            }
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any(), any())
            }
        }
        verifySaveUsersIsNotCalled()
    }

    @Test
    fun `invoice data is correctly propagated to event publishers (invoiceData case)`() {
        coEvery { navQueryService.fetchInvoiceDigestsAndData(technicalUser, any()) } returns
                listOf(Pair(outBoundInvoiceDigest, invoiceData))
        coEvery { navQueryService.fetchInvoiceDigestsAndData(otherTechnicalUser, any()) } returns
                listOf(Pair(inboundInvoiceDigest, invoiceData), Pair(otherInboundInvoiceDigest, invoiceData))
        every { invoiceFeed.getUsers() } returns allUsers
        every { eventPublishers[0].isOnlyDigestDataRequired } returns false

        createAndRunInvoiceFeedPoller(true)

        allUsers.forEach { user ->
            coVerify(exactly = 1) {
                navQueryService.fetchInvoiceDigestsAndData(eq(user), eq(nowTruncated))
            }
        }
        coVerify(exactly = 0) {
            navQueryService.fetchInvoiceDigests(any(), any())
        }

        verify(exactly = 1) {
            invoiceFeed.compareAndSetPollingCompleteUntilForUsers(
                eq(setOf(technicalUser, otherTechnicalUser, hasNoInvoiceTechnicalUser)),
                eq(nowTruncated)
            )
        }
        eventPublishers.forEach { publisher ->
            verify(exactly = 1) {
                publisher.publishInvoiceArrivedEvent(
                    outBoundInvoiceDigest,
                    invoiceData,
                    technicalUser,
                    InvoiceDirection.OUTBOUND
                )
                publisher.publishInvoiceArrivedEvent(
                    inboundInvoiceDigest,
                    invoiceData,
                    otherTechnicalUser,
                    InvoiceDirection.INBOUND
                )
                publisher.publishInvoiceArrivedEvent(
                    otherInboundInvoiceDigest,
                    invoiceData,
                    otherTechnicalUser,
                    InvoiceDirection.INBOUND
                )
            }
            verify(exactly = 0) {
                publisher.publishInvoiceArrivedEvent(any(), any(), any())
            }
        }
        //if saveUsersAfterPolling is true then save user is called
        verifySaveUsersIsCalled()
    }

    @Test
    fun `when event publisher throws ErrorOccurredInEventHandlerException, event publishing is not interrupted and the exception is logged (invoiceDigest case)`() {
        val exception = ErrorOccurredInEventHandlerException(Exception("message"))
        coEvery { navQueryService.fetchInvoiceDigests(technicalUser, any()) } returns listOf(inboundInvoiceDigest)
        every { invoiceFeed.getUsers() } returns setOf(technicalUser)
        every { eventPublishers[0].publishInvoiceArrivedEvent(any(), any(), any()) } throws exception
        /*val method = mockk<Method>()
        val declaringClass = mockk<Class<*>>()
        every { method.name } returns "MethodName"
        every { method.declaringClass.canonicalName } returns "canonical.name.DeclaringClass"
        every { eventPublishers[0].eventHandlerMethod } returns method*/

        createAndRunInvoiceFeedPoller()

        //TODO: implement checking for whole error message
        assertTrue(logCollector.containsOnce(exception.cause!!.message, Level.WARN))
        verifySaveUsersIsNotCalled()
        verifyEventPublishingOfInvoiceDigestIsNotInterrupted()
    }

    @Test
    fun `when event publisher throws ErrorOccurredInEventHandlerException, event publishing is not interrupted and the exception is logged (invoiceData case)`() {
        val exception = ErrorOccurredInEventHandlerException(Exception("message"))
        coEvery { navQueryService.fetchInvoiceDigestsAndData(technicalUser, any()) } returns
                listOf(Pair(inboundInvoiceDigest, invoiceData))
        every { invoiceFeed.getUsers() } returns setOf(technicalUser)
        every { eventPublishers[0].publishInvoiceArrivedEvent(any(), any(), any(), any()) } throws exception
        every { eventPublishers[0].isOnlyDigestDataRequired } returns false
        /*val method = mockk<Method>()
        val declaringClass = mockk<Class<*>>()
        every { method.name } returns "MethodName"
        every { method.declaringClass.canonicalName } returns "canonical.name.DeclaringClass"
        every { eventPublishers[0].eventHandlerMethod } returns method*/

        createAndRunInvoiceFeedPoller(true)

        //TODO: implement checking for whole error message
        assertTrue(logCollector.containsOnce(exception.cause!!.message, Level.WARN))
        //if a non-interrupting exception occurs (and saveUsersAfterPolling is true) then save is called
        verifySaveUsersIsCalled()
        verifyEventPublishingOfInvoiceDataIsNotInterrupted()
    }

    @Test
    fun `when event publisher throws InvoiceMappingException, event publishing is not interrupted and the exception is logged (invoiceDigest case)`() {
        val exception = InvoiceMappingException(InvoiceFeedPollerTest::class.java, Exception("message"))
        coEvery { navQueryService.fetchInvoiceDigests(technicalUser, any()) } returns listOf(inboundInvoiceDigest)
        every { invoiceFeed.getUsers() } returns setOf(technicalUser)
        every { eventPublishers[0].publishInvoiceArrivedEvent(any(), any(), any()) } throws exception

        createAndRunInvoiceFeedPoller()

        assertTrue(
            logCollector.containsOnce(
                InvoiceFeedPoller.INVOICE_MAPPING_ERROR_MESSAGE_TEMPLATE.format(
                    exception.targetClass.canonicalName,
                    exception.cause!!::class.java.canonicalName,
                    exception.cause!!.message
                ),
                Level.WARN
            )
        )
        verifySaveUsersIsNotCalled()
        verifyEventPublishingOfInvoiceDigestIsNotInterrupted()
    }

    @Test
    fun `when event publisher throws InvoiceMappingException, event publishing is not interrupted and the exception is logged (invoiceData case)`() {
        val exception = InvoiceMappingException(InvoiceFeedPollerTest::class.java, Exception("message"))
        coEvery { navQueryService.fetchInvoiceDigestsAndData(technicalUser, any()) } returns
                listOf(Pair(inboundInvoiceDigest, invoiceData))
        every { invoiceFeed.getUsers() } returns setOf(technicalUser)
        every { eventPublishers[0].publishInvoiceArrivedEvent(any(), any(), any(), any()) } throws exception
        every { eventPublishers[0].isOnlyDigestDataRequired } returns false

        createAndRunInvoiceFeedPoller(true)

        assertTrue(
            logCollector.containsOnce(
                InvoiceFeedPoller.INVOICE_MAPPING_ERROR_MESSAGE_TEMPLATE.format(
                    exception.targetClass.canonicalName,
                    exception.cause!!::class.java.canonicalName,
                    exception.cause!!.message
                ),
                Level.WARN
            )
        )
        //if a non-interrupting exception occurs (and saveUsersAfterPolling is true) then save is called
        verifySaveUsersIsCalled()
        verifyEventPublishingOfInvoiceDataIsNotInterrupted()
    }

    private fun createAndRunInvoiceFeedPoller(saveUsersAfterPolling: Boolean = false): InvoiceFeedPoller {
        return InvoiceFeedPoller(
            invoiceFeed,
            eventPublishers,
            navQueryService,
            currentTimeProvider,
            connectionScope,
            saveUsersAfterPolling
        ).apply { run() }
    }

    private fun createInvoiceDigest(invoiceNumber: String, supplierTaxNumber: String): InvoiceDigest {
        return InvoiceDigest(
            invoiceNumber = invoiceNumber,
            invoiceOperation = "invoiceOperation",
            invoiceCategory = "invoiceCategory",
            invoiceIssueDate = LocalDate.now(),
            supplierTaxNumber = supplierTaxNumber,
            supplierName = "supplierName",
            insDate = Instant.now(),
            batchIndex = BigInteger("10"),
            invoiceNetAmount = BigDecimal("10.9999999995")
        )
    }

    private fun verifySaveUsersIsNotCalled() {
        verify(exactly = 0) {
            invoiceFeed.saveUsers()
        }
    }

    private fun verifySaveUsersIsCalled() {
        verify(exactly = 1) {
            invoiceFeed.saveUsers()
        }
    }

    private fun verifyEventPublishingOfInvoiceDigestIsNotInterrupted() {
        verify(exactly = 1) {
            // pollingCompleteUntil is updated as the invoice was delivered to the handler
            invoiceFeed.compareAndSetPollingCompleteUntilForUsers(
                eq(setOf(technicalUser)),
                eq(nowTruncated)
            )
            eventPublishers[1].publishInvoiceArrivedEvent(inboundInvoiceDigest, technicalUser, InvoiceDirection.INBOUND)
        }
    }

    private fun verifyEventPublishingOfInvoiceDataIsNotInterrupted() {
        verify(exactly = 1) {
            // pollingCompleteUntil is updated as the invoice was delivered to the handler
            invoiceFeed.compareAndSetPollingCompleteUntilForUsers(
                eq(setOf(technicalUser)),
                eq(nowTruncated)
            )
            eventPublishers[1].publishInvoiceArrivedEvent(inboundInvoiceDigest, invoiceData, technicalUser, InvoiceDirection.INBOUND)
        }
    }
}