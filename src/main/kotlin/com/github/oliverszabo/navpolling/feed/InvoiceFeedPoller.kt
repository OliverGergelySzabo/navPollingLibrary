package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.exception.NavInvoiceServiceConnectionException
import com.github.oliverszabo.navpolling.api.exception.NavQueryException
import com.github.oliverszabo.navpolling.communication.NavQueryService
import com.github.oliverszabo.navpolling.eventpublishing.EventPublisher
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

class InvoiceFeedPoller(
    //todo: rethink whether it is justified that this is public
    val invoiceFeed: InvoiceFeed,
    private val eventPublishers: List<EventPublisher>,
    private val navQueryService: NavQueryService
): Runnable {
    companion object {
        const val POLLING_ERROR_START = "An error occurred during NAV API polling: "
        const val NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE = POLLING_ERROR_START + "cannot connect to the NAV API (caused by: %s message: %s)"
        const val NAV_QUERY_ERROR_MESSAGE_TEMPLATE = POLLING_ERROR_START + "the NAV API returned an error (funcCode: %s, errorCode: %s, message: %s)"
        const val POLLING_INTERRUPTED_MESSAGE_TEMPLATE
            = "Polling of the '%s' invoice feed has been interrupted (this can cause duplicate invoice arrived events when the application is restarted)"

        private val log = LoggerFactory.getLogger(InvoiceFeedPoller::class.java)
    }

    private val isOnlyDigestDataRequired = eventPublishers.all { it.isOnlyDigestDataRequired }

    override fun run() {
        if(!invoiceFeed.isRunning()) return
        val to = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val from = to.minusSeconds(86400 * 180) //invoiceFeed.state.pollingCompleteUntil
        try {
            if(isOnlyDigestDataRequired) {
                navQueryService.fetchInvoiceDigests(invoiceFeed.getUsers(), from, to).forEach { (digest, user, direction) ->
                    eventPublishers.forEach {
                        it.publishInvoiceArrivedEvent(digest, user, direction)
                    }
                }
            } else {
                navQueryService.fetchInvoiceDigestsAndData(invoiceFeed.getUsers(), from, to).forEach { (data, digest, user, direction) ->
                    eventPublishers.forEach {
                        it.publishInvoiceArrivedEvent(data, digest, user, direction)
                    }
                }
            }
            /*invoices.forEach {
                println(it.first.invoiceNumber)
                println(it.first.invoiceMain.invoice.invoiceHead.supplierInfo.supplierBankAccountNumber)
            }*/

            //invoiceFeed.state = InvoiceFeed.State(currentInstant)
        } catch (e: NavInvoiceServiceConnectionException) {
            log.error(NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE.format(e.cause!!::class.java.canonicalName, e.cause.message))
        } catch (e: NavQueryException) {
            log.error(NAV_QUERY_ERROR_MESSAGE_TEMPLATE.format(e.funcCode, e.errorCode, e.message))
        } catch (e: InterruptedException) {
            log.warn(POLLING_INTERRUPTED_MESSAGE_TEMPLATE.format(invoiceFeed::class.java.canonicalName))
        }
    }
}