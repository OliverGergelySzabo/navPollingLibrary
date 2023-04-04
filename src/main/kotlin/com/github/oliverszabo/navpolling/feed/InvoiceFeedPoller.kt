package com.github.oliverszabo.navpolling.feed

import com.github.oliverszabo.navpolling.api.InvoiceFeed

class InvoiceFeedPoller(
    //todo: rethink whether it is justified that this is public
    val invoiceFeed: InvoiceFeed
): Runnable {
    override fun run() {
        if(!invoiceFeed.isRunning()) return
        //todo: optimize for the case when only invoice digest data is needed
        invoiceFeed.getUsers().forEach {

        }
    }
}