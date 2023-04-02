package com.github.oliverszabo.navpolling

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.TechnicalUser

class VoidInvoiceFeed private constructor(): InvoiceFeed() {
    override fun initialUsers(): Set<TechnicalUser> {
        return emptySet()
    }
}