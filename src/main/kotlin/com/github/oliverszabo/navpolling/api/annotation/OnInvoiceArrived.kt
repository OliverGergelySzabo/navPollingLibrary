package com.github.oliverszabo.navpolling.api.annotation

import com.github.oliverszabo.navpolling.VoidInvoiceFeed
import com.github.oliverszabo.navpolling.api.InvoiceFeed
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnInvoiceArrived(
    val invoiceFeed: KClass<out InvoiceFeed> = VoidInvoiceFeed::class
)