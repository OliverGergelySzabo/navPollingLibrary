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
        if (taxNumber != other.taxNumber) return false
        if (pollingDirections != other.pollingDirections) return false

        return true
    }

    override fun hashCode(): Int {
        var result = login.hashCode()
        result = 31 * result + taxNumber.hashCode()
        result = 31 * result + pollingDirections.hashCode()
        return result
    }
}