package com.github.oliverszabo.navpolling.integration

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.api.annotation.OnInvoiceArrived
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.stereotype.Component
import java.math.BigInteger
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

@Component
class MockInvoiceFeed: InvoiceFeed() {
    private val saveUsersCalled = AtomicBoolean()
    private val handleInvoiceArrivedCalled = AtomicBoolean()

    override fun loadUsers(): Set<TechnicalUser> {
        return setOf(IntegrationTestConstants.technicalUser)
    }

    override fun saveUsers(users: Set<TechnicalUser>) {
        saveUsersCalled.set(true)
        assertEquals(1, users.size)
        var savedUser: TechnicalUser? = null
        users.forEach { savedUser = it }
        assertTechnicalUser(savedUser!!)

    }

    @OnInvoiceArrived
    fun handleInvoiceArrived(invoice: TargetInvoiceClass, technicalUser: TechnicalUser, invoiceDirection: InvoiceDirection) {
        handleInvoiceArrivedCalled.set(true)
        assertInvoice(invoice)
        assertTechnicalUser(technicalUser)
        assertEquals(IntegrationTestConstants.pollingDirection, invoiceDirection)
        IntegrationTestHelper.signalTestCompletion()
    }

    private fun assertTechnicalUser(user: TechnicalUser) {
        assertThat(user).usingRecursiveComparison()
            .ignoringFields("pollingCompleteUntil")
            .isEqualTo(IntegrationTestConstants.technicalUser)
        assertEquals(IntegrationTestConstants.now, user.pollingCompleteUntil)
    }

    private fun assertInvoice(invoice: TargetInvoiceClass) {
        assertEquals(IntegrationTestConstants.invoiceNumber, invoice.mappedField)
        assertEquals(BigInteger.valueOf(33000), invoice.invoiceGrossAmount)
        assertEquals(LocalDate.parse("2023-12-01"), invoice.paymentDate)
        assertEquals("12345678-12345678-12345678", invoice.supplierBankAccountNumber)
        assertEquals(IntegrationTestConstants.ignoredFieldValue, invoice.ignoredField)
    }

    @PreDestroy
    private fun assertMethodWereCalled() {
        assertTrue(saveUsersCalled.get())
        assertTrue(handleInvoiceArrivedCalled.get())
    }
}