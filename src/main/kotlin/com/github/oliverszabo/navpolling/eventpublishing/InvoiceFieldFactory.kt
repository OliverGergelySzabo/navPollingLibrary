package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.model.InvoiceData
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.TypeUtils
import org.springframework.stereotype.Component
import java.lang.reflect.Field

@Component
class InvoiceFieldFactory {
    private val invoiceDataFields = createInvoiceFields(InvoiceData::class.java)
    private val invoiceDataFieldsByShortName = invoiceDataFields.groupBy { it.shortName }
    private val invoiceDataFieldsByLongName = invoiceDataFields.associateBy { it.longName }
    private val invoiceDigestFields = createInvoiceFields(InvoiceDigest::class.java)

    fun getInvoiceField(shortOrLongName: String): InvoiceField? {
        val digestField = invoiceDigestFields.find { it.shortName == shortOrLongName || it.longName == shortOrLongName }
        if(digestField != null) {
            return digestField
        }
        return if(invoiceDataFieldsByShortName.contains(shortOrLongName)) {
            if(invoiceDataFieldsByShortName[shortOrLongName]!!.size > 1) {
                throw IllegalArgumentException("ambiguous short name supplied")
            }
            invoiceDataFieldsByShortName[shortOrLongName]!!.first()
        } else {
            invoiceDataFieldsByLongName[shortOrLongName]
        }
    }

    private fun createInvoiceFields(invoiceClass: Class<*>): List<InvoiceField> {
        val nodes = invoiceClass.declaredFields.reversed().map { Node(it) }.toMutableList()
        val fields = mutableListOf<InvoiceField>()
        while (nodes.isNotEmpty()) {
            val node = nodes.removeLast()
            fields.add(InvoiceField(node.field, invoiceClass, getParentFieldNames(node)))
            nodes.addAll(node.children)
        }

        fields.forEach {
            println("${it.shortName}, ${it.longName}, ${it.javaField.type.simpleName}")
        }
        val ambiguousShortNames = mutableListOf<String>()
        fields.groupBy { it.shortName }.forEach{ key, value ->
            if(value.size > 1) {
                ambiguousShortNames.add(key)
            }
        }
        println(ambiguousShortNames)
        /*
        InvoiceDigest::class.java.declaredFields.forEach { f ->
            if(ambiguousShortNames.contains(f.name)) {
                println(f.name)
            }
        }*/
        return fields
    }

    private fun getParentFieldNames(node: Node): List<String> {
        val parentFieldNames = mutableListOf<String>()
        var currentNode = node.parent
        while (currentNode != null) {
            parentFieldNames.add(currentNode.field.name)
            currentNode = currentNode.parent
        }
        return parentFieldNames.reversed()
    }

    private class Node(
        val field: Field,
        val parent: Node? = null
    ) {
        val children: List<Node>
            get() {
                return if(TypeUtils.isSimpleType(this.field.type)) {
                    emptyList()
                } else {
                    this.field.type.declaredFields.reversed().map { Node(it, this) }
                }
            }
    }
}