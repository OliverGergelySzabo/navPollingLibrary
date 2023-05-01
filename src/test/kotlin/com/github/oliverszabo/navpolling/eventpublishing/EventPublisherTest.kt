package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.assertThrownException
import com.github.oliverszabo.navpolling.util.createXmlMapper
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.lang.reflect.Method
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Paths
import java.time.*

class EventPublisherTest {
    private val invoiceData = createXmlMapper().readValue(
        Paths.get("src","test","resources", "Peldaszamlak_v3.0", "Belfoldi devizas szamla.xml").toFile(),
        InvoiceData::class.java
    )
    private val invoiceDigest = InvoiceDigest(
        invoiceNumber = "invoiceNumber",
        invoiceOperation = "invoiceOperation",
        invoiceCategory = "invoiceCategory",
        invoiceIssueDate = LocalDate.now(),
        supplierTaxNumber = "supplierTaxNumber",
        supplierName = "supplierName",
        insDate = Instant.now(),
        batchIndex = BigInteger("10"),
        invoiceNetAmount = BigDecimal("10.9999999995")
    )
    private val technicalUser = TechnicalUser("l", "p", "t", "s")

    private val invoiceFieldFactory = InvoiceFieldFactory()
    private val xmlMapper = createXmlMapper()
    private val eventHandlerObject = Any()
    private val eventHandlerMethod = mockk<Method>(relaxed = true)

    @Test
    fun constructorThrowsExceptionWhenTheEventHandlerMethodHasNoArgument() {
        every { eventHandlerMethod.parameterTypes } returns emptyArray()
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_MUST_HAVE_AT_LEAST_ONE_ARGUMENT_ERROR_MESSAGE)
    }

    @Test
    fun constructorThrowsExceptionWhenTheEventHandlerMethodHasMoreThanThreeArgumentTypes() {
        every { eventHandlerMethod.parameterTypes } returns
                arrayOf(Any::class.java, TechnicalUser::class.java, InvoiceData::class.java, InvoiceDirection::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_CAN_HAVE_THREE_ARGUMENT_TYPES_MOST_ERROR_MESSAGE)
    }

    @Test
    fun constructorThrowsExceptionWhenTheEventHandlerMethodInvalidArgumentTypeCombination() {
        // 2 argument case (one of them must be TechnicalUser or InvoiceDirection
        every { eventHandlerMethod.parameterTypes } returns arrayOf(Long::class.java, Any::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_HAS_INVALID_ARGUMENT_TYPES_ERROR_MESSAGE)

        // 3 argument cases (two of them must be TechnicalUser and InvoiceDirection)
        every { eventHandlerMethod.parameterTypes } returns arrayOf(Long::class.java, Any::class.java, TechnicalUser::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_HAS_INVALID_ARGUMENT_TYPES_ERROR_MESSAGE)

        every { eventHandlerMethod.parameterTypes } returns arrayOf(Long::class.java, Any::class.java, InvoiceDirection::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_HAS_INVALID_ARGUMENT_TYPES_ERROR_MESSAGE)
    }

    @Test
    fun constructorThrowsExceptionWhenTheEventHandlerMethodDoesNotHaveInvoiceTypeArgument() {
        every { eventHandlerMethod.parameterTypes } returns arrayOf(TechnicalUser::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_ARGUMENTS_MUST_CONTAIN_INVOICE_TYPE_ERROR_MESSAGE)

        every { eventHandlerMethod.parameterTypes } returns arrayOf(InvoiceDirection::class.java, TechnicalUser::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_ARGUMENTS_MUST_CONTAIN_INVOICE_TYPE_ERROR_MESSAGE)

        every { eventHandlerMethod.parameterTypes } returns
                arrayOf(InvoiceDirection::class.java, TechnicalUser::class.java, InvoiceDirection::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_ARGUMENTS_MUST_CONTAIN_INVOICE_TYPE_ERROR_MESSAGE)
    }

    @Test
    fun constructorThrowsExceptionWhenTheEventHandlerMethodInvoiceTypeArgumentIsSimpleType() {
        every { eventHandlerMethod.parameterTypes } returns arrayOf(Int::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_INVOICE_ARGUMENT_CANNOT_BE_SIMPLE_TYPE_ERROR_MESSAGE)

        every { eventHandlerMethod.parameterTypes } returns arrayOf(Int::class.java, TechnicalUser::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_INVOICE_ARGUMENT_CANNOT_BE_SIMPLE_TYPE_ERROR_MESSAGE)

        every { eventHandlerMethod.parameterTypes } returns arrayOf(Int::class.java, InvoiceDirection::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_INVOICE_ARGUMENT_CANNOT_BE_SIMPLE_TYPE_ERROR_MESSAGE)

        every { eventHandlerMethod.parameterTypes } returns arrayOf(Int::class.java, InvoiceDirection::class.java, TechnicalUser::class.java)
        createEventPublisherAndAssertThrownException(EventPublisher.EVENT_HANDLER_INVOICE_ARGUMENT_CANNOT_BE_SIMPLE_TYPE_ERROR_MESSAGE)
    }

    @Test
    fun publishInvoiceArrivedEventInvokesEventHandlerWithTheCorrectArguments() {
        class OnlyInvoiceDigestFields(
            val invoiceNumber: String,
            val invoiceIssueDate: LocalDate,
            val insDate: Instant,
            val batchIndex: BigInteger,
            val invoiceNetAmount: BigDecimal
        )
        class OnlyInvoiceDigestFieldsWithNarrowingConversions(
            val invoiceNumber: String,
            val invoiceIssueDate: YearMonth,
            val insDate: LocalDate,
            val batchIndex: Short,
            val invoiceNetAmount: Short
        )
        class OnlyInvoiceDataFields(
            val supplierTaxNumber: InvoiceData.TaxNumber,
            val customerTaxNumber: InvoiceData.CustomerTaxNumber,
            val supplierBankAccountNumber: String,
            //narrowing conversion
            val invoiceGrossAmount: Int,
        )
        class MixedFields(
            // should always come from InvoiceDigest
            val invoiceNumber: String,
            // due to String type supplierTaxNumber should from InvoiceDigest
            val supplierTaxNumber: String,
            // due to InvoiceData.CustomerTaxNumber type supplierTaxNumber should from InvoiceData
            val customerTaxNumber: InvoiceData.CustomerTaxNumber,

            //narrowing conversions
            val invoiceIssueDate: Year,
            val insDate: Month,
            val invoiceNetAmount: Double,
            val invoiceGrossAmount: Long,

            //widening conversions
            val batchIndex: BigDecimal,

        )

        val capturedEventHandlerObject = slot<Any>()
        val capturedInvoice = slot<Any>()
        val capturedTechnicalUser = slot<TechnicalUser>()
        val capturedInvoiceDirection = slot<InvoiceDirection>()

        every {
            eventHandlerMethod.invoke(
                capture(capturedEventHandlerObject),
                capture(capturedInvoice),
                capture(capturedTechnicalUser),
                capture(capturedInvoiceDirection)
            )
        } returns null

        every { eventHandlerMethod.parameterTypes } returns arrayOf(
            OnlyInvoiceDigestFields::class.java,
            TechnicalUser::class.java,
            InvoiceDirection::class.java
        )
        createEventPublisher().publishInvoiceArrivedEvent(invoiceDigest, technicalUser, InvoiceDirection.INBOUND)
        assertEventHandlerCalledEventHandlerObject(capturedEventHandlerObject)
        assertEventHandlerArguments(
            OnlyInvoiceDigestFields(
                invoiceNumber = invoiceDigest.invoiceNumber,
                invoiceIssueDate = invoiceDigest.invoiceIssueDate,
                insDate = invoiceDigest.insDate,
                batchIndex = invoiceDigest.batchIndex!!,
                invoiceNetAmount = invoiceDigest.invoiceNetAmount!!
            ),
            technicalUser,
            InvoiceDirection.INBOUND,
            capturedInvoice,
            capturedTechnicalUser,
            capturedInvoiceDirection
        )

        every { eventHandlerMethod.parameterTypes } returns arrayOf(
            OnlyInvoiceDigestFieldsWithNarrowingConversions::class.java,
            TechnicalUser::class.java,
            InvoiceDirection::class.java
        )
        createEventPublisher().publishInvoiceArrivedEvent(invoiceDigest, technicalUser, InvoiceDirection.INBOUND)
        assertEventHandlerCalledEventHandlerObject(capturedEventHandlerObject)
        assertEventHandlerArguments(
            OnlyInvoiceDigestFieldsWithNarrowingConversions(
                invoiceNumber = invoiceDigest.invoiceNumber,
                invoiceIssueDate = YearMonth.from(invoiceDigest.invoiceIssueDate),
                insDate = invoiceDigest.insDate.atZone(ZoneId.systemDefault()).toLocalDate(),
                batchIndex = invoiceDigest.batchIndex!!.toShort(),
                invoiceNetAmount = invoiceDigest.invoiceNetAmount!!.toShort()
            ),
            technicalUser,
            InvoiceDirection.INBOUND,
            capturedInvoice,
            capturedTechnicalUser,
            capturedInvoiceDirection
        )

        every { eventHandlerMethod.parameterTypes } returns arrayOf(
            OnlyInvoiceDataFields::class.java,
            TechnicalUser::class.java,
            InvoiceDirection::class.java
        )
        createEventPublisher().publishInvoiceArrivedEvent(invoiceData, invoiceDigest, technicalUser, InvoiceDirection.OUTBOUND)
        assertEventHandlerCalledEventHandlerObject(capturedEventHandlerObject)
        assertEventHandlerArguments(
            OnlyInvoiceDataFields(
                supplierTaxNumber = InvoiceData.TaxNumber("99999999", "2", "41"),
                customerTaxNumber = InvoiceData.CustomerTaxNumber("99887764", "2", "02"),
                supplierBankAccountNumber = "12345678-12345678-12345678",
                invoiceGrossAmount = 23622,
            ),
            technicalUser,
            InvoiceDirection.OUTBOUND,
            capturedInvoice,
            capturedTechnicalUser,
            capturedInvoiceDirection
        )

        every { eventHandlerMethod.parameterTypes } returns arrayOf(
            MixedFields::class.java,
            TechnicalUser::class.java,
            InvoiceDirection::class.java
        )
        createEventPublisher().publishInvoiceArrivedEvent(invoiceData, invoiceDigest, technicalUser, InvoiceDirection.OUTBOUND)
        assertEventHandlerCalledEventHandlerObject(capturedEventHandlerObject)
        assertEventHandlerArguments(
            MixedFields(
                invoiceNumber = invoiceDigest.invoiceNumber,
                supplierTaxNumber = invoiceDigest.supplierTaxNumber,
                customerTaxNumber = InvoiceData.CustomerTaxNumber("99887764", "2", "02"),
                invoiceIssueDate = Year.from(invoiceDigest.invoiceIssueDate),
                insDate = Month.from(invoiceDigest.insDate.atZone(ZoneId.systemDefault())),
                invoiceNetAmount = invoiceDigest.invoiceNetAmount!!.toDouble(),
                invoiceGrossAmount = 23622,
                batchIndex = invoiceDigest.batchIndex!!.toBigDecimal()
            ),
            technicalUser,
            InvoiceDirection.OUTBOUND,
            capturedInvoice,
            capturedTechnicalUser,
            capturedInvoiceDirection
        )
    }

    private fun assertEventHandlerCalledEventHandlerObject(capturedEventHandlerObject: CapturingSlot<Any>) {
        assertEquals(eventHandlerObject, capturedEventHandlerObject.captured)
    }

    private fun assertEventHandlerArguments(
        expectedInvoice: Any,
        expectedTechnicalUser: TechnicalUser,
        expectedInvoiceDirection: InvoiceDirection,
        capturedInvoice: CapturingSlot<Any>,
        capturedTechnicalUser: CapturingSlot<TechnicalUser>,
        capturedInvoiceDirection: CapturingSlot<InvoiceDirection>
    ) {
        assertThat(capturedInvoice.captured)
            .usingRecursiveComparison()
            .isEqualTo(expectedInvoice)
        assertThat(capturedTechnicalUser.captured)
            .usingRecursiveComparison()
            .isEqualTo(expectedTechnicalUser)
        assertEquals(expectedInvoiceDirection, capturedInvoiceDirection.captured)
    }

    private fun createEventPublisherAndAssertThrownException(expectedMessage: String) {
        assertThrownException<IllegalArgumentException>(expectedMessage) {
            createEventPublisher()
        }
    }

    private fun createEventPublisher(): EventPublisher {
        return EventPublisher(eventHandlerObject, eventHandlerMethod, invoiceFieldFactory, xmlMapper)
    }
}