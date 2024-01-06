package com.github.oliverszabo.navpolling.polling

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.api.exception.ErrorOccurredInEventHandlerException
import com.github.oliverszabo.navpolling.api.exception.InvoiceMappingException
import com.github.oliverszabo.navpolling.api.exception.NavInvoiceServiceConnectionException
import com.github.oliverszabo.navpolling.api.exception.NavQueryException
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisher
import com.github.oliverszabo.navpolling.util.CurrentTimeProvider
import com.github.oliverszabo.navpolling.util.calculateInvoiceDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

class InvoiceFeedPoller(
    val invoiceFeed: InvoiceFeed,
    private val eventPublishers: List<EventPublisher>,
    private val navQueryService: NavQueryService,
    private val currentTimeProvider: CurrentTimeProvider,
    private val connectionScope: CoroutineScope,
    private val saveUsersAfterPolling: Boolean,
): Runnable {
    companion object {
        const val POLLING_ERROR_START = "The following error occurred during polling: "
        const val NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE = POLLING_ERROR_START +
                "could not establish connection to the NAV invoice service (caused by: %s message: %s)"
        const val NAV_QUERY_ERROR_MESSAGE_TEMPLATE = POLLING_ERROR_START +
                "the NAV API returned an error for technical user %s - %s (funcCode: %s, errorCode: %s, message: %s)"
        const val POLLING_INTERRUPTED_MESSAGE_TEMPLATE
            = "Polling of the '%s' invoice feed has been interrupted (this can cause duplicate invoice arrived events when the application is restarted)"
        const val ERROR_OCCURRED_IN_EVENT_HANDLER_MESSAGE_TEMPLATE = POLLING_ERROR_START +
                "'%s' event handler threw an exception: %s"
        const val INVOICE_MAPPING_ERROR_MESSAGE_TEMPLATE = POLLING_ERROR_START +
                "could not convert invoice data to class '%s' (caused by: %s message: %s)"

        private val log = LoggerFactory.getLogger(InvoiceFeedPoller::class.java)
    }

    private val isOnlyDigestDataRequired = eventPublishers.all { it.isOnlyDigestDataRequired }

    override fun run() {
        if(!invoiceFeed.isRunning()) return
        try {
            if(isOnlyDigestDataRequired) {
                executeDataFetchAndPublish(
                    { user, to ->
                        navQueryService.fetchInvoiceDigests(user, to)
                    },
                    { eventPublisher, digest, user ->
                        eventPublisher.publishInvoiceArrivedEvent(digest, user, calculateInvoiceDirection(digest, user.taxNumber))
                    }
                )
            } else {
                executeDataFetchAndPublish(
                    { user, to -> navQueryService.fetchInvoiceDigestsAndData(user, to) },
                    { eventPublisher, (digest, data), user ->
                        eventPublisher.publishInvoiceArrivedEvent(
                            digest,
                            data,
                            user,
                            calculateInvoiceDirection(digest, user.taxNumber)
                        )
                    }
                )
            }
        } catch (e: NavInvoiceServiceConnectionException) {
            log.error(NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE.format(e.cause!!::class.java.canonicalName, e.cause!!.message))
        } catch (e: InterruptedException) {
            log.warn(POLLING_INTERRUPTED_MESSAGE_TEMPLATE.format(invoiceFeed::class.java.canonicalName))
            Thread.currentThread().interrupt()
        }
    }

    private fun <T> executeDataFetchAndPublish(
        fetcher: suspend (TechnicalUser, Instant) -> List<T>,
        publisher: (EventPublisher, T, TechnicalUser) -> Unit
    ) {
        val to = currentTimeProvider.currentSecond()
        val users = invoiceFeed.getUsers()
        val userWithSuccessfulPolling = ConcurrentLinkedQueue<TechnicalUser>()
        runBlocking {
            return@runBlocking users.map { user ->
                connectionScope.async {
                    try {
                        val fetchResult = Pair(fetcher(user, to), user)
                        userWithSuccessfulPolling.add(user)
                        return@async fetchResult
                    } catch (e: NavQueryException) {
                        log.error(NAV_QUERY_ERROR_MESSAGE_TEMPLATE.format(user.login, user.taxNumber, e.funcCode, e.errorCode, e.message))
                        return@async null
                    }
                }
            }.awaitAll()
        }
            .filterNotNull()
            .forEach { (fetchedDataList, user) ->
                fetchedDataList.forEach { data ->
                    eventPublishers.forEach { eventPublisher ->
                        try {
                            publisher(eventPublisher, data, user)
                        } catch (e: ErrorOccurredInEventHandlerException) {
                            log.warn(
                                ERROR_OCCURRED_IN_EVENT_HANDLER_MESSAGE_TEMPLATE.format(
                                    "${eventPublisher.eventHandlerMethod.declaringClass.canonicalName}.${eventPublisher.eventHandlerMethod.name}",
                                    e.cause!!.message
                                ),
                                e.cause
                            )
                        } catch (e: InvoiceMappingException) {
                            log.warn(
                                INVOICE_MAPPING_ERROR_MESSAGE_TEMPLATE.format(
                                    e.targetClass.canonicalName,
                                    e.cause!!::class.java.canonicalName,
                                    e.cause!!.message
                                ),
                                e.cause
                            )
                        }
                    }
                }
            }
        invoiceFeed.compareAndSetPollingCompleteUntilForUsers(userWithSuccessfulPolling.toSet(), to)
        if(saveUsersAfterPolling) {
            invoiceFeed.saveUsers()
        }
    }
}