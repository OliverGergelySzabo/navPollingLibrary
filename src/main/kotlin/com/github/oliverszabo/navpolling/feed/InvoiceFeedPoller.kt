package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.api.exception.NavInvoiceServiceConnectionException
import com.github.oliverszabo.navpolling.api.exception.NavQueryException
import com.github.oliverszabo.navpolling.communication.NavQueryService
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisher
import com.github.oliverszabo.navpolling.util.minusDays
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

class InvoiceFeedPoller(
    val invoiceFeed: InvoiceFeed,
    private val eventPublishers: List<EventPublisher>,
    private val navQueryService: NavQueryService,
): Runnable {
    companion object {
        const val POLLING_ERROR_START = "An error occurred during polling: "
        const val NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE =
            POLLING_ERROR_START + "could not establish connection to the NAV invoice service (caused by: %s message: %s)"
        const val NAV_QUERY_ERROR_MESSAGE_TEMPLATE = POLLING_ERROR_START + "the NAV API returned an error (funcCode: %s, errorCode: %s, message: %s)"
        const val POLLING_INTERRUPTED_MESSAGE_TEMPLATE
            = "Polling of the '%s' invoice feed has been interrupted (this can cause duplicate invoice arrived events when the application is restarted)"

        private val log = LoggerFactory.getLogger(InvoiceFeedPoller::class.java)
    }

    private val isOnlyDigestDataRequired = eventPublishers.all { it.isOnlyDigestDataRequired }

    override fun run() {
        if(!invoiceFeed.isRunning()) return
        val to = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        try {
            fetchAndPublishInvoices(invoiceFeed.getUsers(false), invoiceFeed.getPollingCompleteUntil().truncatedTo(ChronoUnit.SECONDS), to)
            val userWithPastFetchingRequired = invoiceFeed.getUsers(true)
            fetchAndPublishInvoices(userWithPastFetchingRequired, to.minusDays(invoiceFeed.getPastFetchingPeriod()), to)
            invoiceFeed.onPastFetchingCompleted(userWithPastFetchingRequired)
            invoiceFeed.setPollingCompleteUntil(to)
        } catch (e: NavInvoiceServiceConnectionException) {
            log.error(NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE.format(e.cause!!::class.java.canonicalName, e.cause!!.message))
        } catch (e: NavQueryException) {
            log.error(NAV_QUERY_ERROR_MESSAGE_TEMPLATE.format(e.funcCode, e.errorCode, e.message))
        } catch (e: InterruptedException) {
            log.warn(POLLING_INTERRUPTED_MESSAGE_TEMPLATE.format(invoiceFeed::class.java.canonicalName))
            Thread.currentThread().interrupt()
        }
    }

    private fun fetchAndPublishInvoices(users: Set<TechnicalUser>, from: Instant, to: Instant) {
        if(isOnlyDigestDataRequired) {
            navQueryService.fetchInvoiceDigests(users, from, to).forEach { (digest, user, direction) ->
                eventPublishers.forEach {
                    it.publishInvoiceArrivedEvent(digest, user, direction)
                }
            }
        } else {
            navQueryService.fetchInvoiceDigestsAndData(users, from, to).forEach { (data, digest, user, direction) ->
                eventPublishers.forEach {
                    it.publishInvoiceArrivedEvent(data, digest, user, direction)
                }
            }
        }
    }
}