package com.github.oliverszabo.navpolling

import ch.qos.logback.classic.Level
import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.polling.NavQueryService
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisher
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisherFactory
import com.github.oliverszabo.navpolling.polling.InvoiceFeedPoller
import com.github.oliverszabo.navpolling.testutil.LogCollector
import com.github.oliverszabo.navpolling.util.CurrentTimeProvider
import com.github.oliverszabo.navpolling.util.forceGet
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.PeriodicTrigger
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LifecycleManagerTest {
    class Feed: InvoiceFeed(1)
    class OtherFeed: InvoiceFeed(1)

    private val feeds = listOf(mockk<Feed>(relaxed = true), mockk<OtherFeed>(relaxed = true))
    private val librarySettings = mockk<LibrarySettings>(relaxed = true)
    private val navQueryService = mockk<NavQueryService>(relaxed = true)
    private val eventPublisherFactory = mockk<EventPublisherFactory>(relaxed = true)
    private val currentTimeProvider = mockk<CurrentTimeProvider>(relaxed = true)

    private val logCollector = LogCollector.create(LifecycleManager::class.java)
    private val trigger = PeriodicTrigger(1, TimeUnit.DAYS)
    private val scheduledFuture = mockk<ScheduledFuture<*>>(relaxed = true)
    private val connectionScope = mockk<CoroutineScope>(relaxed = true)
    private val feedEventPublisher = mockk<EventPublisher>(relaxed = true)
    private val otherFeedEventPublisher = mockk<EventPublisher>(relaxed = true)

    private val shutdownTimout = 10

    private var manager: LifecycleManager? = null

    @BeforeEach
    fun beforeEach() {
        mockkConstructor(ThreadPoolTaskScheduler::class)
        every { anyConstructed<ThreadPoolTaskScheduler>().initialize() } returns Unit
        every { anyConstructed<ThreadPoolTaskScheduler>().schedule(any(), any<Trigger>()) } returns scheduledFuture
        every { anyConstructed<ThreadPoolTaskScheduler>().getPoolSize() } returns 100
        every { anyConstructed<ThreadPoolTaskScheduler>().shutdown() } returns Unit

        mockkConstructor(InvoiceFeedPoller::class)

        mockkStatic(::CoroutineScope)
        every { CoroutineScope(any()) } returns connectionScope
        every { connectionScope.cancel() } returns Unit

        every { librarySettings.connectionPoolSize } returns 1
        every { librarySettings.pollingPoolSize } returns 3
        every { librarySettings.pollingFrequency } returns trigger
        every { librarySettings.shutdownTimeout } returns shutdownTimout
        every { librarySettings.saveUsersAfterPolling } returns true

        every { eventPublisherFactory.getEventPublishers(any()) } returns emptyList()
        every { scheduledFuture.cancel(any()) } returns true
    }

    @AfterEach
    fun afterEach() {
        manager = null
        clearAllMocks()
        logCollector.reset()
    }

    @Test
    fun createdLifecycleManagerInitializesCorrectly() {
        createLifeCycleManager()
        assertFalse(manager!!.isRunning)
        verify(exactly = 1) {
            anyConstructed<ThreadPoolTaskScheduler>().poolSize = eq(feeds.size)
            anyConstructed<ThreadPoolTaskScheduler>().threadNamePrefix = eq(LifecycleManager.POLLING_POOL_THREAD_NAME_PREFIX)
            anyConstructed<ThreadPoolTaskScheduler>().initialize()
        }
    }

    @Test
    fun createdLifecycleManagerInitializesCorrectlyWhenTheNumberOfFeedsIsGreaterThanThePoolSize() {
        every { librarySettings.pollingPoolSize } returns 1
        createLifeCycleManager()

        assertFalse(manager!!.isRunning)
        verify(exactly = 1) {
            anyConstructed<ThreadPoolTaskScheduler>().poolSize = eq(1)
            anyConstructed<ThreadPoolTaskScheduler>().threadNamePrefix = eq(LifecycleManager.POLLING_POOL_THREAD_NAME_PREFIX)
            anyConstructed<ThreadPoolTaskScheduler>().initialize()
        }
    }

    @Test
    fun startStartsAllInvoiceFeeds() {
        val createdPollers = mutableListOf<InvoiceFeedPoller>()
        val createdTriggers = mutableListOf<Trigger>()
        every { anyConstructed<ThreadPoolTaskScheduler>().schedule(any<InvoiceFeedPoller>(), any<Trigger>()) } answers {
            createdPollers.add(firstArg())
            createdTriggers.add(secondArg())
            mockk(relaxed = true)
        }
        every { eventPublisherFactory.getEventPublishers(any()) } answers {
            val feedClass = firstArg<Class<out InvoiceFeed>>()
            if(feedClass == Feed::class.java) {
                listOf(feedEventPublisher)
            } else {
                listOf(otherFeedEventPublisher)
            }
        }

        createLifeCycleManager()
        manager!!.start()

        assertEquals(feeds.size, createdPollers.size)
        feeds.forEach { feed ->
            verifyOrder {
                feed.init()
                eventPublisherFactory.getEventPublishers(eq(feed.javaClass))
            }
            val poller = createdPollers.find { it.invoiceFeed == feed }
            assertCreatedPoller(feed, poller)
            createdPollers.remove(poller)
        }
        assertEquals(feeds.size, createdTriggers.size)
        createdTriggers.forEach {
            assertEquals(trigger, it)
        }
        verify(exactly = feeds.size) {
            anyConstructed<ThreadPoolTaskScheduler>().schedule(any<InvoiceFeedPoller>(), any<Trigger>())
        }
        assertTrue(logCollector.containsOnce(LifecycleManager.START_MESSAGE_TEMPLATE.format(feeds.size), Level.INFO))
        assertTrue(manager!!.isRunning)
    }

    @Test
    fun stopStopsAllInvoiceFeeds() {
        createLifeCycleManager()
        manager!!.start()
        manager!!.stop()

        verifyOrder {
            feeds.forEach {
                scheduledFuture.cancel(eq(false))
            }
            anyConstructed<ThreadPoolTaskScheduler>().shutdown()
            connectionScope.cancel()
            feeds.forEach {
                it.destroy()
            }
        }
        assertTrue(logCollector.containsOnce(LifecycleManager.STOP_MESSAGE_TEMPLATE.format(feeds.size, shutdownTimout), Level.INFO))
        assertFalse(manager!!.isRunning)
    }

    private fun createLifeCycleManager() {
        manager = LifecycleManager(feeds, librarySettings, navQueryService, eventPublisherFactory, currentTimeProvider)
    }

    private fun getField(obj: Any, fieldName: String): Any? {
        return obj::class.java.declaredFields.find { it.name == fieldName }!!.forceGet(obj)
    }

    private fun assertCreatedPoller(expectedInvoiceFeed: InvoiceFeed, poller: InvoiceFeedPoller?) {
        assertNotNull(poller)
        assertEquals(expectedInvoiceFeed, poller!!.invoiceFeed)
        if(expectedInvoiceFeed is Feed) {
            assertEquals(listOf(feedEventPublisher), getField(poller, "eventPublishers"))
        } else {
            assertEquals(listOf(otherFeedEventPublisher), getField(poller, "eventPublishers"))
        }
        assertEquals(navQueryService, getField(poller, "navQueryService"))
        assertEquals(currentTimeProvider, getField(poller, "currentTimeProvider"))
        assertEquals(connectionScope, getField(poller, "connectionScope"))
        assertEquals(librarySettings.saveUsersAfterPolling, getField(poller, "saveUsersAfterPolling"))
    }
}