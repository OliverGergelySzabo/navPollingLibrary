package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.TypeUtils
import com.github.oliverszabo.navpolling.util.forceGet
import java.lang.reflect.Field

class InvoiceField(
    val javaField: Field,
    val modelClass: Class<*>,
    val parentFieldNames: List<String>,
) {
    val longName = "${modelClass.simpleName}.${(parentFieldNames + javaField.name).joinToString(separator = ".")}"
    val shortName: String
        get() = javaField.name
    val isInvoiceDigestField = modelClass == InvoiceDigest::class.java

    fun getValue(invoice: Any): Any? {
        return getValue(invoice, javaField.type)
    }

    fun <T> getValue(invoice: Any, valueType: Class<T>): T? {
        if(invoice.javaClass != modelClass) {
            throw IllegalArgumentException("The supplied invoice object is not the model object of this InvoiceField")
        }
        if(!TypeUtils.isCastableTo(javaField.type, valueType)) {
            throw IllegalArgumentException("This field cannot be cast to the supplied valueType")
        }
        if(parentFieldNames.isEmpty()) {
            return javaField.forceGet(invoice) as T?
        }
        var field = invoice.javaClass.declaredFields.find { it.name == parentFieldNames.first() }!!
        var value = field.forceGet(invoice) ?: return null
        parentFieldNames.subList(1, parentFieldNames.size).forEach { fieldName ->
            field = field.type.declaredFields.find { it.name == fieldName }!!
            value = field.forceGet(value) ?: return null
        }
        return javaField.forceGet(value) as T?
    }
}