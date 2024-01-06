package com.github.oliverszabo.navpolling.polling.dto

import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.util.sha512Hash

data class NavTechnicalUser(
    val login: String,
    val passwordHash: String,
    val taxNumber: String,
    val sigKey: String
) {
    companion object {
        fun from(technicalUser: TechnicalUser, hashPassword: Boolean): NavTechnicalUser {
            return NavTechnicalUser(
                login = technicalUser.login,
                passwordHash = if(hashPassword) sha512Hash(technicalUser.password) else technicalUser.password,
                taxNumber = technicalUser.taxNumber,
                sigKey = technicalUser.sigKey
            )
        }
    }
}