package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.communication.NavQueryService
import com.github.oliverszabo.navpolling.config.LibrarySettings
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
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LifecycleManagerTest {
    private val feeds = IntRange(0, 1).map { mockk<InvoiceFeed>(relaxed = true) }
    private val librarySettings = mockk<LibrarySettings>(relaxed = true)
    private val navQueryService = mockk<NavQueryService>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val trigger = PeriodicTrigger(1, TimeUnit.DAYS)
    private val executor = mockk<ScheduledExecutorService>(relaxed = true)

    private val shutdownTimout = 10L

    private var manager: LifecycleManager? = null

    @BeforeEach
    fun beforeEach() {
        mockkConstructor(ThreadPoolTaskScheduler::class)
        every { anyConstructed<ThreadPoolTaskScheduler>().initialize() } returns Unit
        every { anyConstructed<ThreadPoolTaskScheduler>().schedule(any(), any<Trigger>()) } returns mockk(relaxed = true)
        every { anyConstructed<ThreadPoolTaskScheduler>().getPoolSize() } returns 100
        every { anyConstructed<ThreadPoolTaskScheduler>().scheduledExecutor } returns executor

        every { executor.shutdown() } returns Unit
        every { executor.shutdownNow() } returns emptyList()
        every { executor.awaitTermination(any(), any()) } returns true

        mockkConstructor(InvoiceFeedPoller::class)

        //for some reason mockkStatic only works properly if TestInstance.Lifecycle.PER_CLASS is set
        //todo: find out why
        mockkStatic(LoggerFactory::class)
        every { LoggerFactory.getLogger(LifecycleManager::class.java) } returns logger

        every { librarySettings.pollingPoolSize } returns 3
        every { librarySettings.pollingFrequency } returns trigger
        every { librarySettings.shutdownTimeout } returns shutdownTimout
    }

    @AfterEach
    fun afterEach() {
        manager = null
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
            verify(exactly = 1) {
                feed.init()
            }
            verify(exactly = feeds.size) {
                anyConstructed<ThreadPoolTaskScheduler>().schedule(any<InvoiceFeedPoller>(), any<Trigger>())
            }
            val poller = createdPollers.find { it.invoiceFeed == feed }
            assertNotNull(poller)
            createdPollers.remove(poller)
        }
        verify(exactly = 1) { logger.info(eq(LifecycleManager.START_MESSAGE_TEMPLATE.format(feeds.size))) }
        assertTrue(manager!!.isRunning)
    }

    @Test
    fun stopStopsAllInvoiceFeeds() {
        createLifeCycleManager()
        manager!!.start()
        manager!!.stop()

        verify(exactly = 1) {
            executor.shutdown()
            executor.shutdownNow()
            executor.awaitTermination(eq(shutdownTimout), eq(TimeUnit.SECONDS))
            executor.awaitTermination(eq(LifecycleManager.ADDITIONAL_TIMEOUT), eq(TimeUnit.SECONDS))
        }
        feeds.forEach {
            verify(exactly = 1) { it.destroy() }
        }
        assertFalse(manager!!.isRunning)
    }

    private fun createLifeCycleManager() {
        manager = LifecycleManager(feeds, librarySettings, navQueryService)
    }
}