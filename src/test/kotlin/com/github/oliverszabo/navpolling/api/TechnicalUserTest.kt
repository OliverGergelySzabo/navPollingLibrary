package com.github.oliverszabo.navpolling.api

import com.github.oliverszabo.navpolling.api.TechnicalUser.Builder.Companion.BOTH_PASSWORD_AND_PASSWORD_HASH_PARAMS_ARE_SUPPLIED_ERROR
import com.github.oliverszabo.navpolling.api.TechnicalUser.Builder.Companion.PASSWORD_AND_PASSWORD_HASH_PARAMS_ARE_MISSING_ERROR
import com.github.oliverszabo.navpolling.api.exception.TechnicalUserCreationException
import com.github.oliverszabo.navpolling.util.ErrorMessages
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class TechnicalUserTest {
    private val login = "login"
    private val password = "somePassword"
    private val passwordHash = "6AE49F48528396834395FC09A3AF2014B583B5B22ACCC91FF201A777CB30F5D12932672224087BDE0F4CF81CD1CD1E85E6F420455A1A411C12BD968EB135472F"
    private val taxNumber = "taxNumber"
    private val sigKey = "sigKey"
    private val pollingDirections = setOf(InvoiceDirection.INBOUND)
    private val pollingCompleteUntil = Instant.now()

    @Test
    fun `hashPassword hashes the password correctly`() {
        assertEquals(passwordHash, TechnicalUser.hashPassword(password))
    }

    @Test
    fun `Builder_build throws exception if mandatory parameters are missing`() {
        // login is missing
        var builder = TechnicalUser.builder()
            .password(password)
            .taxNumber(taxNumber)
            .sigKey(sigKey)
        runBuildAndAssertException(builder, ErrorMessages.fieldMustBeNonNull(TechnicalUser::login.name))

        // taxNumber is missing
        builder = TechnicalUser.builder()
            .login(login)
            .password(password)
            .sigKey(sigKey)
        runBuildAndAssertException(builder, ErrorMessages.fieldMustBeNonNull(TechnicalUser::taxNumber.name))

        // sigKey is missing
        builder = TechnicalUser.builder()
            .login(login)
            .password(password)
            .taxNumber(taxNumber)
        runBuildAndAssertException(builder, ErrorMessages.fieldMustBeNonNull(TechnicalUser::sigKey.name))
    }

    @Test
    fun `Builder_build throws exception if both password and passwordHash parameters are missing`() {
        val builder = TechnicalUser.builder()
            .login(login)
            .taxNumber(taxNumber)
            .sigKey(sigKey)
        runBuildAndAssertException(builder, PASSWORD_AND_PASSWORD_HASH_PARAMS_ARE_MISSING_ERROR)
    }

    @Test
    fun `Builder_build throws exception if both password and passwordHash parameters are supplied`() {
        val builder = TechnicalUser.builder()
            .login(login)
            .taxNumber(taxNumber)
            .sigKey(sigKey)
            .password(password)
            .passwordHash(passwordHash)
        runBuildAndAssertException(builder, BOTH_PASSWORD_AND_PASSWORD_HASH_PARAMS_ARE_SUPPLIED_ERROR)
    }

    @Test
    fun `Builder_build creates correct TechnicalUser`() {
        // only mandatory params
        var builder = TechnicalUser.builder()
            .login(login)
            .taxNumber(taxNumber)
            .sigKey(sigKey)
            .passwordHash(passwordHash)
        assertThat(builder.build()).usingRecursiveComparison()
            .isEqualTo(TechnicalUser(login, passwordHash, taxNumber, sigKey))

        // all params
        builder = TechnicalUser.builder()
            .login(login)
            .taxNumber(taxNumber)
            .sigKey(sigKey)
            .passwordHash(passwordHash)
            .pollingDirections(pollingDirections)
            .pollingCompleteUntil(pollingCompleteUntil)
        assertThat(builder.build()).usingRecursiveComparison()
            .isEqualTo(TechnicalUser(login, passwordHash, taxNumber, sigKey, pollingDirections, pollingCompleteUntil))

        // password is automatically hashed if it is supplied in unhashed format
        builder = TechnicalUser.builder()
            .login(login)
            .taxNumber(taxNumber)
            .sigKey(sigKey)
            .password(password)
        assertThat(builder.build()).usingRecursiveComparison()
            .isEqualTo(TechnicalUser(login, passwordHash, taxNumber, sigKey))
    }

    private fun runBuildAndAssertException(builder: TechnicalUser.Builder, expectedMessage: String) {
        val message = assertThrows<TechnicalUserCreationException> {
            builder.build()
        }.message
        assertEquals(expectedMessage, message)
    }
}