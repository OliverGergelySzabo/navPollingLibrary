package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LifecycleManagerTest {
    private val feeds = IntRange(0, 1).map { mockk<InvoiceFeed>(relaxed = true) }
    private val mockLogger = mockk<Logger>(relaxed = true)
    private lateinit var manager: LifecycleManager

    @BeforeEach
    fun init() {
        mockkStatic(LoggerFactory::class)
        every { LoggerFactory.getLogger(LifecycleManager::class.java) } returns mockLogger
        manager = LifecycleManager(feeds)
    }

    @Test
    fun startStartsAllInvoiceFeeds() {
        assertFalse(manager.isRunning)

        manager.start()

        feeds.forEach {
            verify(exactly = 1) { it.loadUsers() }
            verify(exactly = 1) { it.start() }
        }
        verify(exactly = 1) { mockLogger.info(eq(LifecycleManager.START_MESSAGE_TEMPLATE.format(feeds.size))) }
        assertTrue(manager.isRunning)
    }

    @Test
    fun stopStopsAllInvoiceFeeds() {
        manager.start()
        manager.stop()

        feeds.forEach {
            verify(exactly = 1) { it.stop() }
        }
        assertFalse(manager.isRunning)
    }
}