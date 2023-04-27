package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.forceGet
import java.lang.reflect.Field

class InvoiceDigestField(
    javaField: Field,
): InvoiceField(javaField) {
    override val modelClass: Class<*> = InvoiceDigest::class.java
    override val longName: String = "${modelClass.simpleName}.$shortName"

    override fun getValue(invoice: Any): Any? {
        checkInvoiceTypeMatchesModelClass(invoice)
        return javaField.forceGet(invoice)
    }
}