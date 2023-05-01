package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.communication.NavQueryService
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisherFactory
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import java.lang.Integer.max
import java.util.concurrent.TimeUnit

@Component
class LifecycleManager(
    private val invoiceFeeds: List<InvoiceFeed>,
    private val librarySettings: LibrarySettings,
    private val navQueryService: NavQueryService,
    private val eventPublisherFactory: EventPublisherFactory,
): SmartLifecycle {
    companion object {
        private val log = LoggerFactory.getLogger(LifecycleManager::class.java)
        const val START_MESSAGE_TEMPLATE = "NAV invoice polling started for %d feed(s)"
        const val STOP_MESSAGE_TEMPLATE = "Stopping NAV invoice polling for %d feed(s) (maximum timeout: %ds)"
        const val POLLING_POOL_THREAD_NAME_PREFIX = "NavPollingLibraryPool"
        const val ADDITIONAL_TIMEOUT = 1L
    }

    private val pollingScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = max(1, invoiceFeeds.size.coerceAtMost(librarySettings.pollingPoolSize))
        threadNamePrefix = POLLING_POOL_THREAD_NAME_PREFIX
        initialize()
    }
    private var isRunning: Boolean = false

    @Synchronized
    override fun start() {
        invoiceFeeds.forEach { feed ->
            feed.init()
            pollingScheduler.schedule(
                InvoiceFeedPoller(feed, eventPublisherFactory.getEventPublishers(feed.javaClass), navQueryService),
                librarySettings.pollingFrequency
            )
        }
        isRunning = true
        log.info(START_MESSAGE_TEMPLATE.format(invoiceFeeds.size))
    }

    @Synchronized
    override fun stop() {
        log.info(STOP_MESSAGE_TEMPLATE.format(invoiceFeeds.size, librarySettings.shutdownTimeout))
        stopPollingScheduler()
        invoiceFeeds.forEach { it.destroy() }
        isRunning = false
    }

    @Synchronized
    override fun isRunning(): Boolean {
        return isRunning
    }

    private fun stopPollingScheduler() {
        val executor = pollingScheduler.scheduledExecutor
        executor.shutdown()
        //todo: this never shuts down before termination, this has to be investigated
        executor.awaitTermination(librarySettings.shutdownTimeout, TimeUnit.SECONDS)
        executor.shutdownNow()
        // wait for an additional second so all jobs are interrupted properly before returning
        executor.awaitTermination(ADDITIONAL_TIMEOUT, TimeUnit.SECONDS)
    }
}