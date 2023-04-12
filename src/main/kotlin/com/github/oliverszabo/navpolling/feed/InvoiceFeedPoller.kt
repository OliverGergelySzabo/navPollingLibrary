package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.communication.NavQueryService
import java.time.Instant

class InvoiceFeedPoller(
    //todo: rethink whether it is justified that this is public
    val invoiceFeed: InvoiceFeed,
    private val navQueryService: NavQueryService
): Runnable {
    override fun run() {
        if(!invoiceFeed.isRunning()) return
        //todo: optimize for the case when only invoice digest data is needed
        val currentInstant = Instant.now()
            navQueryService.fetchInvoices(invoiceFeed.getUsers(), currentInstant.minusSeconds(86400 * 180)/*invoiceFeed.state.pollingCompleteUntil*/, currentInstant).forEach {
                println(it.first.invoiceNumber)
                println(it.first.invoiceMain.invoice.invoiceHead.supplierInfo.supplierBankAccountNumber)
            }
        invoiceFeed.state = InvoiceFeed.State(currentInstant)
    }
}