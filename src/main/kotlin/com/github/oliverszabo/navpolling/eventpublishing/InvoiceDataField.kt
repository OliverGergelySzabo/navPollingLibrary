package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.util.forceGet
import java.lang.reflect.Field

class InvoiceDataField(
    javaField: Field,
    private val parentFieldNames: List<String>,
): InvoiceField(javaField) {
    override val modelClass: Class<*> = InvoiceData::class.java
    override val longName: String = "${modelClass.simpleName}.${(parentFieldNames + shortName).joinToString(separator = ".")}"

    override fun getValue(invoice: Any): Any? {
        checkInvoiceTypeMatchesModelClass(invoice)
        if(parentFieldNames.isEmpty()) {
            return javaField.forceGet(invoice)
        }
        var field = invoice.javaClass.declaredFields.find { it.name == parentFieldNames.first() }!!
        var value = field.forceGet(invoice) ?: return null
        parentFieldNames.subList(1, parentFieldNames.size).forEach { fieldName ->
            field = field.type.declaredFields.find { it.name == fieldName }!!
            value = field.forceGet(value) ?: return null
        }
        return javaField.forceGet(value)
    }

}