package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
class LifecycleManager(
    private val feeds: List<InvoiceFeed>
): SmartLifecycle {
    companion object {
        private val log = LoggerFactory.getLogger(LifecycleManager::class.java)
        const val START_MESSAGE_TEMPLATE = "Nav polling started for %d feed(s)"
    }

    private var isRunning: Boolean = false

    override fun start() {
        feeds.forEach { feed ->
            feed.loadUsers()
            feed.start()
        }
        isRunning = true
        log.info(START_MESSAGE_TEMPLATE.format(feeds.size))
    }

    override fun stop() {
        feeds.forEach { it.stop() }
        isRunning = false
    }

    override fun isRunning(): Boolean {
        return isRunning
    }
}