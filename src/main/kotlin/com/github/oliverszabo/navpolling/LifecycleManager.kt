package com.github.oliverszabo.navpolling

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.polling.NavQueryService
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisherFactory
import com.github.oliverszabo.navpolling.polling.InvoiceFeedPoller
import com.github.oliverszabo.navpolling.util.CurrentTimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import java.lang.Integer.max
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture

@Component
class LifecycleManager(
    private val invoiceFeeds: List<InvoiceFeed>,
    private val librarySettings: LibrarySettings,
    private val navQueryService: NavQueryService,
    private val eventPublisherFactory: EventPublisherFactory,
    private val currentTimeProvider: CurrentTimeProvider
): SmartLifecycle {
    companion object {
        private val log = LoggerFactory.getLogger(LifecycleManager::class.java)
        const val START_MESSAGE_TEMPLATE = "NAV invoice polling started for %d feed(s)"
        const val STOP_MESSAGE_TEMPLATE = "Stopping NAV invoice polling for %d feed(s) (maximum timeout: %ds)"
        const val TIMEOUT_REACHED_MESSAGE_TEMPLATE = "Timeout reached while waiting for ongoing polling to finish " +
                "(this can cause duplicate invoice arrived events for some feeds when the application is restarted)"
        const val POLLING_POOL_THREAD_NAME_PREFIX = "NavPollingLibraryPool"
    }

    private val pollingScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = max(1, invoiceFeeds.size.coerceAtMost(librarySettings.pollingPoolSize))
        threadNamePrefix = POLLING_POOL_THREAD_NAME_PREFIX
        setAwaitTerminationSeconds(librarySettings.shutdownTimeout)
        setWaitForTasksToCompleteOnShutdown(true)
        initialize()
    }
    private var isRunning: Boolean = false
    private val scheduledPollingTasks = mutableListOf<ScheduledFuture<*>>()
    private val connectionScope = CoroutineScope(
        SupervisorJob() + Executors.newFixedThreadPool(librarySettings.connectionPoolSize).asCoroutineDispatcher()
    )

    @Synchronized
    override fun start() {
        invoiceFeeds.forEach { feed ->
            feed.init()
            scheduledPollingTasks.add(
                pollingScheduler.schedule(
                    InvoiceFeedPoller(
                        feed,
                        eventPublisherFactory.getEventPublishers(feed.javaClass),
                        navQueryService,
                        currentTimeProvider,
                        connectionScope,
                        librarySettings.saveUsersAfterPolling
                    ),
                    librarySettings.pollingFrequency
                )!!
            )
        }
        isRunning = true
        log.info(START_MESSAGE_TEMPLATE.format(invoiceFeeds.size))
    }

    @Synchronized
    override fun stop() {
        log.info(STOP_MESSAGE_TEMPLATE.format(invoiceFeeds.size, librarySettings.shutdownTimeout))
        stopScheduledTasks()
        invoiceFeeds.forEach { it.destroy() }
        isRunning = false
    }

    @Synchronized
    override fun isRunning(): Boolean {
        return isRunning
    }

    private fun stopScheduledTasks() {
        scheduledPollingTasks.forEach { it.cancel(false) }
        val shutDownStart = System.currentTimeMillis()
        pollingScheduler.shutdown()
        connectionScope.cancel()
        if(System.currentTimeMillis() - shutDownStart > (librarySettings.shutdownTimeout * 1000)) {
            log.warn(TIMEOUT_REACHED_MESSAGE_TEMPLATE)
        }
    }
}