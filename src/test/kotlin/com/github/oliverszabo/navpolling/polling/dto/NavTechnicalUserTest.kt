package com.github.oliverszabo.navpolling.polling.dto

import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.util.sha512Hash
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class NavTechnicalUserTest {
    private val password = "password"
    private val hashedPassword = sha512Hash(password)
    private val technicalUser = TechnicalUser("login", password, "taxNumber", "sigKey")

    @Test
    fun fromHashesPasswordIfNeeded() {
        assertNavTechnicalUser(technicalUser, hashedPassword, NavTechnicalUser.from(technicalUser, true))
    }

    @Test
    fun fromDoesNotHashPasswordIfNotNeeded() {
        assertNavTechnicalUser(technicalUser, password, NavTechnicalUser.from(technicalUser, false))
    }

    private fun assertNavTechnicalUser(technicalUser: TechnicalUser, expectedPassword: String, navTechnicalUser: NavTechnicalUser) {
        assertEquals(technicalUser.login, navTechnicalUser.login)
        assertEquals(expectedPassword, navTechnicalUser.passwordHash)
        assertEquals(technicalUser.taxNumber, navTechnicalUser.taxNumber)
        assertEquals(technicalUser.sigKey, navTechnicalUser.sigKey)
    }
}