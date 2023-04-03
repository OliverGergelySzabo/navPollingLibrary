package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.config.LibrarySettings
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component

@Component
class LifecycleManager(
    private val invoiceFeeds: List<InvoiceFeed>,
    private val librarySettings: LibrarySettings
): SmartLifecycle {
    companion object {
        private val log = LoggerFactory.getLogger(LifecycleManager::class.java)
        const val START_MESSAGE_TEMPLATE = "NAV invoice polling started for %d feed(s)"
        const val POLLING_POOL_THREAD_NAME_PREFIX = "NavPollingLibraryPool"
    }
    private val pollingScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = invoiceFeeds.size.coerceAtMost(librarySettings.pollingPoolSize)
        threadNamePrefix = POLLING_POOL_THREAD_NAME_PREFIX
        initialize()
    }

    private var isRunning: Boolean = false

    override fun start() {
        invoiceFeeds.forEach { feed ->
            feed.loadUsers()
            feed.start()
            pollingScheduler.schedule(InvoiceFeedPoller(feed), librarySettings.pollingFrequency)
        }
        isRunning = true
        log.info(START_MESSAGE_TEMPLATE.format(invoiceFeeds.size))
    }

    override fun stop() {
        invoiceFeeds.forEach { it.stop() }
        isRunning = false
    }

    override fun isRunning(): Boolean {
        return isRunning
    }
}