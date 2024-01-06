package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.util.TypeUtils
import com.github.oliverszabo.navpolling.util.firstActualTypeArgument
import java.lang.reflect.Field

abstract class InvoiceField(
    val javaField: Field
) {
    abstract val longName: String
    protected abstract val modelClass: Class<*>
    val shortName: String = javaField.name
    val genericTypeArgument = javaField.firstActualTypeArgument()
    val isGenericField = genericTypeArgument != null

    abstract fun getValue(invoice: Any): Any?

    fun isConvertibleTo(targetField: Field): Boolean {
        val originalType = javaField.type
        val targetType = targetField.type

        if(targetType == Any::class.java) return true

        val isOriginalTypeSimple = TypeUtils.isSimpleType(originalType)
        val isTargetTypeSimple = TypeUtils.isSimpleType(targetType)
        if (isOriginalTypeSimple xor isTargetTypeSimple) return false

        if (isOriginalTypeSimple) {
            if (String::class.java == targetType) return true
            if (TypeUtils.isNumericType(originalType) && TypeUtils.isNumericType(targetType)) return true
            if (TypeUtils.isDateType(originalType) && TypeUtils.isDateType(targetType)) return true
            return TypeUtils.isBoolean(originalType) && TypeUtils.isBoolean(targetType)
        }

        if (isGenericField) {
            return genericTypeArgument == targetField.firstActualTypeArgument() && originalType == targetType
        }

        return TypeUtils.isCastableTo(originalType, targetType)
    }

    protected fun checkInvoiceTypeMatchesModelClass(invoice: Any) {
        if(invoice.javaClass != modelClass) {
            throw IllegalArgumentException("The supplied invoice object's type is not compatible with the model class of this InvoiceField")
        }
    }
}