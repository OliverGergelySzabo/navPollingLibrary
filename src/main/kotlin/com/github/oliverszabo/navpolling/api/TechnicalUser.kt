package com.github.oliverszabo.navpolling.api

class TechnicalUser(
    val login: String,
    val password: String,
    val taxNumber: String,
    val sigKey: String,
    val pollingDirections: Set<InvoiceDirection> = InvoiceDirection.values().toSet()
) {
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