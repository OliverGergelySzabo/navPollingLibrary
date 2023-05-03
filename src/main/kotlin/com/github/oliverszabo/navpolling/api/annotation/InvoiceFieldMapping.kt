package com.github.oliverszabo.navpolling.api.annotation

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class InvoiceFieldMapping(
    val value: String = "",
    val fieldName: String = ""
)
