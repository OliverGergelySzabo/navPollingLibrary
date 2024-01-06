package com.github.oliverszabo.navpolling.api.exception

open class NavPollingLibraryException(message: String? = null, cause: Throwable? = null): Exception(message, cause)

class NavPollingLibraryInitializationException(message: String?, cause: Throwable? = null): NavPollingLibraryException(message, cause)

class NavInvoiceServiceConnectionException(cause: Throwable): NavPollingLibraryException(cause = cause)

class NavQueryException(val funcCode: String?, val errorCode: String?, message: String?): NavPollingLibraryException(message)

class ErrorOccurredInEventHandlerException(cause: Throwable): NavPollingLibraryException(cause = cause)

class InvoiceMappingException(val targetClass: Class<*>, cause: Throwable): NavPollingLibraryException(cause = cause)