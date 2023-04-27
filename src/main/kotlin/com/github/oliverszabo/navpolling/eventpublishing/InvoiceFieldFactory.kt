package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.FieldNode
import com.github.oliverszabo.navpolling.util.mapFieldsRecursively
import org.springframework.stereotype.Component
import java.lang.reflect.Field

@Component
class InvoiceFieldFactory {
    companion object {
        const val INVOICE_FIELD_NOT_FOUND_ERROR_TEMPLATE = "The field '%s' is not a valid invoice field"
        const val AMBIGUOUS_SHORT_NAME_ERROR_TEMPLATE = "More than one invoice field has '%s' as its short name (possible values: '%s')"
        const val INVOICE_FIELD_NOT_CONVERTIBLE_TO_TARGET_FIELD_ERROR_TEMPLATE
                = "The field '%s' with type '%s' cannot be created from invoice field with type '%s' because conversion is not possible between them"
    }

    private val invoiceDataFields = InvoiceData::class.java.mapFieldsRecursively { node ->
        InvoiceDataField(node.field, getParentFieldNames(node))
    }
    private val invoiceDataFieldsByShortName = invoiceDataFields.groupBy { it.shortName }
    private val invoiceDataFieldsByLongName = invoiceDataFields.associateBy { it.longName }
    private val invoiceDigestFields = InvoiceDigest::class.java.mapFieldsRecursively { node ->
        InvoiceDigestField(node.field)
    }

    fun getInvoiceField(shortOrLongName: String, targetField: Field): InvoiceField {
        val digestField = invoiceDigestFields.find { it.shortName == shortOrLongName || it.longName == shortOrLongName }
        if(digestField != null && digestField.isConvertibleTo(targetField)) {
            return digestField
        }

        val dataField = if(invoiceDataFieldsByShortName.contains(shortOrLongName)) {
            if(invoiceDataFieldsByShortName[shortOrLongName]!!.size > 1) {
                throw IllegalArgumentException(
                    AMBIGUOUS_SHORT_NAME_ERROR_TEMPLATE.format(
                        shortOrLongName,
                        invoiceDataFieldsByShortName[shortOrLongName]!!.joinToString(separator = "', '") { it.longName }
                    )
                )
            }
            invoiceDataFieldsByShortName[shortOrLongName]!!.first()
        } else {
            invoiceDataFieldsByLongName[shortOrLongName]
                ?: throw IllegalArgumentException(INVOICE_FIELD_NOT_FOUND_ERROR_TEMPLATE.format(shortOrLongName))
        }
        if(!dataField.isConvertibleTo(targetField)) {
            throw IllegalArgumentException(
                INVOICE_FIELD_NOT_CONVERTIBLE_TO_TARGET_FIELD_ERROR_TEMPLATE.format(targetField.name, targetField.type, dataField.javaField.type)
            )
        }
        return dataField
    }

    private fun getParentFieldNames(node: FieldNode): List<String> {
        val parentFieldNames = mutableListOf<String>()
        var currentNode = node.parent
        while (currentNode != null) {
            parentFieldNames.add(currentNode.field.name)
            currentNode = currentNode.parent
        }
        return parentFieldNames.reversed()
    }
}