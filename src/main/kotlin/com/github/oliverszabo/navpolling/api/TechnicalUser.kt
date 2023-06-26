package com.github.oliverszabo.navpolling.api

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class TechnicalUser(
    val login: String,
    val password: String,
    val taxNumber: String,
    val sigKey: String,
    val pollingDirections: Set<InvoiceDirection> = InvoiceDirection.values().toSet(),
    val pollingCompleteUntil: Instant? = null,
) {
    fun withPollingCompleteUntil(newValue: Instant): TechnicalUser {
        return TechnicalUser(login, password, taxNumber, sigKey, pollingDirections, newValue)
    }

    fun withPollingDirections(newValue: Set<InvoiceDirection>): TechnicalUser {
        return TechnicalUser(login, password, taxNumber, sigKey, newValue, pollingCompleteUntil)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TechnicalUser
        if (login != other.login) return false
        return true
    }

    override fun hashCode(): Int {
        return login.hashCode()
    }
}