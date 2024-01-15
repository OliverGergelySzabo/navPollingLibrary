package com.github.oliverszabo.navpolling.api

import com.github.oliverszabo.navpolling.api.exception.TechnicalUserCreationException
import com.github.oliverszabo.navpolling.util.ErrorMessages
import com.github.oliverszabo.navpolling.util.sha512Hash
import java.time.Instant

class TechnicalUser(
    val login: String,
    val passwordHash: String,
    val taxNumber: String,
    val sigKey: String,
    val pollingDirections: Set<InvoiceDirection> = InvoiceDirection.values().toSet(),
    val pollingCompleteUntil: Instant? = null,
) {
    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }

        @JvmStatic
        fun hashPassword(password: String): String {
            return sha512Hash(password)
        }
    }

    fun withPollingCompleteUntil(newValue: Instant): TechnicalUser {
        return TechnicalUser(login, passwordHash, taxNumber, sigKey, pollingDirections, newValue)
    }

    fun withPollingDirections(newValue: Set<InvoiceDirection>): TechnicalUser {
        return TechnicalUser(login, passwordHash, taxNumber, sigKey, newValue, pollingCompleteUntil)
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

    class Builder {
        companion object {
            const val PASSWORD_AND_PASSWORD_HASH_PARAMS_ARE_MISSING_ERROR
                = "One of the 'password' or 'passwordHash' fields must not be null"
            const val BOTH_PASSWORD_AND_PASSWORD_HASH_PARAMS_ARE_SUPPLIED_ERROR
                = "The 'password' and 'passwordHash' fields can not be both specified"
        }

        private var login: String? = null
        private var password: String? = null
        private var passwordHash: String? = null
        private var taxNumber: String? = null
        private var sigKey: String? = null
        private var pollingDirections: Set<InvoiceDirection> = InvoiceDirection.values().toSet()
        private var pollingCompleteUntil: Instant? = null

        fun login(login: String): Builder {
            this.login = login
            return this
        }

        fun password(password: String): Builder {
            this.password = password
            return this
        }

        fun passwordHash(passwordHash: String): Builder {
            this.passwordHash = passwordHash
            return this
        }

        fun taxNumber(taxNumber: String): Builder {
            this.taxNumber = taxNumber
            return this
        }

        fun sigKey(sigKey: String): Builder {
            this.sigKey = sigKey
            return this
        }

        fun pollingDirections(pollingDirections: Set<InvoiceDirection>): Builder {
            this.pollingDirections = pollingDirections
            return this
        }

        fun pollingCompleteUntil(pollingCompleteUntil: Instant): Builder {
            this.pollingCompleteUntil = pollingCompleteUntil
            return this
        }

        fun build(): TechnicalUser {
            if (login == null) {
                throw TechnicalUserCreationException(ErrorMessages.fieldMustBeNonNull(TechnicalUser::login.name))
            }
            if (taxNumber == null) {
                throw TechnicalUserCreationException(ErrorMessages.fieldMustBeNonNull(TechnicalUser::taxNumber.name))
            }
            if (sigKey == null) {
                throw TechnicalUserCreationException(ErrorMessages.fieldMustBeNonNull(TechnicalUser::sigKey.name))
            }
            if (password == null && passwordHash == null) {
                throw TechnicalUserCreationException(PASSWORD_AND_PASSWORD_HASH_PARAMS_ARE_MISSING_ERROR)
            }
            if (password != null && passwordHash != null) {
                throw TechnicalUserCreationException(BOTH_PASSWORD_AND_PASSWORD_HASH_PARAMS_ARE_SUPPLIED_ERROR)
            }

            if(password != null) {
                passwordHash = hashPassword(password!!)
            }
            return TechnicalUser(login!!, passwordHash!!, taxNumber!!, sigKey!!, pollingDirections, pollingCompleteUntil)
        }
    }
}