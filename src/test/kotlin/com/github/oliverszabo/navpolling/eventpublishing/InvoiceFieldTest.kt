package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.model.InvoiceDigest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.reflect.jvm.javaField
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class InvoiceFieldTest {
    private open class Base
    private class Child: Base()

    private class OriginalClass(
        val complexTypeField: Child,
        val numericField: BigDecimal,
        val booleanField: Boolean,
        val stringField: String,
        val dateField: Instant,
        val genericField: List<Int>
    )

    private class TargetClass(
        val anyField: Any,
        val childField: Child,
        val baseField: Base,
        val otherComplexField: InvoiceDigest,
        val numericField: Int,
        val booleanField: Boolean,
        val stringField: String,
        val dateField: LocalDate,
        val intListField: List<Int>,
        val longListField: List<Long>,
        val intSet: Set<Int>
    )

    @Test
    fun whenInvoiceFieldHasComplexTypeIsConvertibleToReturnsTrueOnlyIfItIsCastableToTargetType() {
        val field = InvoiceDigestField(OriginalClass::complexTypeField.javaField!!)

        assertTrue(field.isConvertibleTo(TargetClass::anyField.javaField!!))
        assertTrue(field.isConvertibleTo(TargetClass::childField.javaField!!))
        assertTrue(field.isConvertibleTo(TargetClass::baseField.javaField!!))

        assertFalse(field.isConvertibleTo(TargetClass::otherComplexField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::numericField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::booleanField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::stringField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::dateField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::longListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intSet.javaField!!))
    }

    @Test
    fun whenInvoiceFieldHasNumericTypeIsConvertibleToReturnsTrueOnlyIfTargetTypeIsNumericOrStringOrAny() {
        val field = InvoiceDigestField(OriginalClass::numericField.javaField!!)

        assertTrue(field.isConvertibleTo(TargetClass::anyField.javaField!!))
        assertTrue(field.isConvertibleTo(TargetClass::numericField.javaField!!))
        assertTrue(field.isConvertibleTo(TargetClass::stringField.javaField!!))

        assertFalse(field.isConvertibleTo(TargetClass::childField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::baseField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::otherComplexField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::booleanField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::dateField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::longListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intSet.javaField!!))
    }

    @Test
    fun whenInvoiceFieldHasBooleanTypeIsConvertibleToReturnsTrueOnlyIfTargetTypeIsBooleanOrStringOrAny() {
        val field = InvoiceDigestField(OriginalClass::booleanField.javaField!!)

        assertTrue(field.isConvertibleTo(TargetClass::anyField.javaField!!))
        assertTrue(field.isConvertibleTo(TargetClass::booleanField.javaField!!))
        assertTrue(field.isConvertibleTo(TargetClass::stringField.javaField!!))

        assertFalse(field.isConvertibleTo(TargetClass::childField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::baseField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::otherComplexField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::numericField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::dateField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::longListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intSet.javaField!!))
    }

    @Test
    fun whenInvoiceFieldHasStringTypeIsConvertibleToReturnsTrueOnlyIfTargetTypeIsStringOrAny() {
        val field = InvoiceDigestField(OriginalClass::stringField.javaField!!)

        assertTrue(field.isConvertibleTo(TargetClass::anyField.javaField!!))
        assertTrue(field.isConvertibleTo(TargetClass::stringField.javaField!!))

        assertFalse(field.isConvertibleTo(TargetClass::booleanField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::childField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::baseField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::otherComplexField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::numericField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::dateField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::longListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intSet.javaField!!))
    }

    @Test
    fun whenInvoiceFieldHasDateTypeIsConvertibleToReturnsTrueOnlyIfTargetTypeIsDateOrStringOrAny() {
        val field = InvoiceDigestField(OriginalClass::dateField.javaField!!)

        assertTrue(field.isConvertibleTo(TargetClass::anyField.javaField!!))
        assertTrue(field.isConvertibleTo(TargetClass::stringField.javaField!!))
        assertTrue(field.isConvertibleTo(TargetClass::dateField.javaField!!))

        assertFalse(field.isConvertibleTo(TargetClass::booleanField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::childField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::baseField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::otherComplexField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::numericField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::longListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intSet.javaField!!))
    }

    @Test
    fun whenInvoiceFieldHasGenericTypeIsConvertibleToReturnsTrueOnlyIfTargetTypeIsGenericAndHasTheSameGenericTypeArgumentOrAny() {
        val field = InvoiceDigestField(OriginalClass::genericField.javaField!!)

        assertTrue(field.isConvertibleTo(TargetClass::anyField.javaField!!))
        assertTrue(field.isConvertibleTo(TargetClass::intListField.javaField!!))

        assertFalse(field.isConvertibleTo(TargetClass::stringField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::dateField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::booleanField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::childField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::baseField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::otherComplexField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::numericField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::longListField.javaField!!))
        assertFalse(field.isConvertibleTo(TargetClass::intSet.javaField!!))
    }
}