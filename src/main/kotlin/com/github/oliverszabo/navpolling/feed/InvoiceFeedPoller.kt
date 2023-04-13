package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.exception.NavInvoiceServiceConnectionException
import com.github.oliverszabo.navpolling.api.exception.NavQueryException
import com.github.oliverszabo.navpolling.communication.NavQueryService
import org.slf4j.LoggerFactory
import java.time.Instant

class InvoiceFeedPoller(
    //todo: rethink whether it is justified that this is public
    val invoiceFeed: InvoiceFeed,
    private val navQueryService: NavQueryService
): Runnable {
    companion object {
        const val POLLING_ERROR_START = "An error occurred during NAV API polling: "
        const val NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE = POLLING_ERROR_START + "cannot connect to the NAV API (caused by: %s message: %s)"
        const val NAV_QUERY_ERROR_MESSAGE_TEMPLATE = POLLING_ERROR_START + "the NAV API returned an error (funcCode: %s, errorCode: %s, message: %s)"

        private val log = LoggerFactory.getLogger(InvoiceFeedPoller::class.java)
    }

    override fun run() {
        if(!invoiceFeed.isRunning()) return
        val currentInstant = Instant.now()
        try {
            //todo: optimize for the case when only invoice digest data is needed
            val invoices = navQueryService.fetchInvoices(
                invoiceFeed.getUsers(),
                currentInstant.minusSeconds(86400 * 180)/*invoiceFeed.state.pollingCompleteUntil*/,
                currentInstant
            )
            invoices.forEach {
                println(it.first.invoiceNumber)
                println(it.first.invoiceMain.invoice.invoiceHead.supplierInfo.supplierBankAccountNumber)
            }
            //todo: call event publishers
            invoiceFeed.state = InvoiceFeed.State(currentInstant)
        } catch (e: NavInvoiceServiceConnectionException) {
            log.error(NAV_CONNECTION_ERROR_MESSAGE_TEMPLATE.format(e.cause!!::class.java.canonicalName, e.cause!!.message))
        } catch (e: NavQueryException) {
            log.error(NAV_QUERY_ERROR_MESSAGE_TEMPLATE.format(e.funcCode, e.errorCode, e.message))
        }
    }
}