package com.github.oliverszabo.navpolling.polling

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.exception.ErrorOccurredInEventHandlerException
import com.github.oliverszabo.navpolling.api.exception.NavInvoiceServiceConnectionException
import com.github.oliverszabo.navpolling.api.exception.NavQueryException
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisher
import com.github.oliverszabo.navpolling.util.CurrentTimeProvider
import org.slf4j.LoggerFactory

class InvoiceFeedPoller(
    val invoiceFeed: InvoiceFeed,
    private val eventPublishers: List<EventPublisher>,
    private val navQueryService: NavQueryService,
    private val currentTimeProvider: CurrentTimeProvider,
): Runnable {
    companion object {
        const val POLLING_ERROR_START = "An error occurred during polling: "
        const val NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE =
            POLLING_ERROR_START + "could not establish connection to the NAV invoice service (caused by: %s message: %s)"
        const val NAV_QUERY_ERROR_MESSAGE_TEMPLATE = POLLING_ERROR_START + "the NAV API returned an error (funcCode: %s, errorCode: %s, message: %s)"
        const val POLLING_INTERRUPTED_MESSAGE_TEMPLATE
            = "Polling of the '%s' invoice feed has been interrupted (this can cause duplicate invoice arrived events when the application is restarted)"
        const val ERROR_OCCURRED_IN_EVENT_HANDLER_MESSAGE_TEMPLATE = "The following error occurred in event handler '%s': %s"

        private val log = LoggerFactory.getLogger(InvoiceFeedPoller::class.java)
    }

    private val isOnlyDigestDataRequired = eventPublishers.all { it.isOnlyDigestDataRequired }

    override fun run() {
        if(!invoiceFeed.isRunning()) return
        val to = currentTimeProvider.currentSecond()
        try {
            val users = invoiceFeed.getUsers()
            if(isOnlyDigestDataRequired) {
                navQueryService.fetchInvoiceDigests(users, to).forEach { (digest, user, direction) ->
                    eventPublishers.forEach {
                        try {
                            it.publishInvoiceArrivedEvent(digest, user, direction)
                        } catch (e: ErrorOccurredInEventHandlerException) {
                            handleErrorOccurredInEventHandlerException(e, it)
                        }
                    }
                }
            } else {
                navQueryService.fetchInvoiceDigestsAndData(users, to).forEach { (data, digest, user, direction) ->
                    eventPublishers.forEach {
                        try {
                            it.publishInvoiceArrivedEvent(data, digest, user, direction)
                        } catch (e: ErrorOccurredInEventHandlerException) {
                            handleErrorOccurredInEventHandlerException(e, it)
                        }
                    }
                }
            }
            invoiceFeed.compareAndSetPollingCompleteUntilForUsers(users, to)
        } catch (e: NavInvoiceServiceConnectionException) {
            log.error(NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE.format(e.cause!!::class.java.canonicalName, e.cause!!.message))
        } catch (e: NavQueryException) {
            log.error(NAV_QUERY_ERROR_MESSAGE_TEMPLATE.format(e.funcCode, e.errorCode, e.message))
        } catch (e: InterruptedException) {
            log.warn(POLLING_INTERRUPTED_MESSAGE_TEMPLATE.format(invoiceFeed::class.java.canonicalName))
            Thread.currentThread().interrupt()
        }
    }

    private fun handleErrorOccurredInEventHandlerException(e: ErrorOccurredInEventHandlerException, eventPublisher: EventPublisher) {
        log.warn(
            ERROR_OCCURRED_IN_EVENT_HANDLER_MESSAGE_TEMPLATE.format(
                "${eventPublisher.eventHandlerMethod.declaringClass.canonicalName}.${eventPublisher.eventHandlerMethod.name}",
                e.cause!!.message
            )
        )
        e.cause!!.printStackTrace()
    }
}