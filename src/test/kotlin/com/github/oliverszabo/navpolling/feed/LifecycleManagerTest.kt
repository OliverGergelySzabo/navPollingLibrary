package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.communication.NavQueryService
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisherFactory
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    private val logger = mockk<Logger>(relaxed = true)
    private val trigger = PeriodicTrigger(1, TimeUnit.DAYS)
    private val scheduledFuture = mockk<ScheduledFuture<*>>(relaxed = true)

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

        //for some reason mockkStatic only works properly if TestInstance.Lifecycle.PER_CLASS is set
        //todo: find out why
        mockkStatic(LoggerFactory::class)
        every { LoggerFactory.getLogger(LifecycleManager::class.java) } returns logger

        every { librarySettings.pollingPoolSize } returns 3
        every { librarySettings.pollingFrequency } returns trigger
        every { librarySettings.shutdownTimeout } returns shutdownTimout

        every { eventPublisherFactory.getEventPublishers(any()) } returns emptyList()
        every { scheduledFuture.cancel(any()) } returns true
    }

    @AfterEach
    fun afterEach() {
        manager = null
        clearAllMocks()
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

        createLifeCycleManager()
        manager!!.start()

        assertEquals(feeds.size, createdPollers.size)
        feeds.forEach { feed ->
            verifyOrder {
                feed.init()
                eventPublisherFactory.getEventPublishers(eq(feed.javaClass))
            }
            val poller = createdPollers.find { it.invoiceFeed == feed }
            assertNotNull(poller)
            createdPollers.remove(poller)
        }
        assertEquals(feeds.size, createdTriggers.size)
        createdTriggers.forEach {
            assertEquals(trigger, it)
        }
        verify(exactly = feeds.size) {
            anyConstructed<ThreadPoolTaskScheduler>().schedule(any<InvoiceFeedPoller>(), any<Trigger>())
        }
        verify(exactly = 1) { logger.info(eq(LifecycleManager.START_MESSAGE_TEMPLATE.format(feeds.size))) }
        assertTrue(manager!!.isRunning)
    }

    @Test
    fun stopStopsAllInvoiceFeeds() {
        createLifeCycleManager()
        manager!!.start()
        manager!!.stop()

        verifyOrder {
            logger.info(eq(LifecycleManager.STOP_MESSAGE_TEMPLATE.format(feeds.size, shutdownTimout)))
            feeds.forEach {
                scheduledFuture.cancel(eq(false))
            }
            anyConstructed<ThreadPoolTaskScheduler>().shutdown()
            feeds.forEach {
                it.destroy()
            }
        }
        assertFalse(manager!!.isRunning)
    }

    private fun createLifeCycleManager() {
        manager = LifecycleManager(feeds, librarySettings, navQueryService, eventPublisherFactory)
    }
}