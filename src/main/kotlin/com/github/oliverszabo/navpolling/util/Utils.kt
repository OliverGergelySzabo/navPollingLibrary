package com.github.oliverszabo.navpolling.util

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.TemporalUnit

fun byteToHex(b: Byte) : String{
    val ret = b.toUByte().toString(16).uppercase()
    return if(ret.length == 1) "0$ret" else ret
}

fun createXmlMapper(): XmlMapper {
    return XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        registerKotlinModule()
        registerModule(JavaTimeModule())
        setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}

fun sha512Hash(s: String) : String {
    return MessageDigest.getInstance("SHA-512").hashString(s)
}

fun MessageDigest.hashString(s: String) : String {
    return digest(s.toByteArray()).joinToString(separator = "") { byteToHex(it) }
}

fun Instant.plusDays(daysToAdd: Long): Instant {
    return plusSeconds(86400 * daysToAdd)
}

fun Instant.minusDays(daysToSubtract: Long): Instant {
    return plusDays(-daysToSubtract)
}

fun Instant.isTruncatedTo(unit: TemporalUnit): Boolean {
    return this == truncatedTo(unit)
}

fun SecureRandom.randomHex(length: Int): String {
    val bytes = ByteArray(length)
    nextBytes(bytes)
    return bytes.joinToString(separator = "") { byteToHex(it) }.substring(0, length)
}

fun Field.forceGet(obj: Any): Any? {
    trySetAccessible()
    return get(obj)
}

fun Field.firstActualTypeArgument(): Class<*>? {
    val genericType = genericType
    if(genericType is ParameterizedType) {
        if(genericType.actualTypeArguments.isEmpty()) return null
        if(genericType.actualTypeArguments.size > 1) {
            throw Exception("This field has more than one actual type arguments")
        }
        return genericType.actualTypeArguments.first() as Class<*>
    }
    return null
}

fun Class<*>.forEachFieldsRecursively(action: (fieldNode: FieldNode) -> Unit) {
    val nodes = declaredFields.reversed().map { FieldNode(it) }.toMutableList()
    while (nodes.isNotEmpty()) {
        val node = nodes.removeLast()
        action(node)
        nodes.addAll(node.children)
    }
}

fun <T> Class<*>.mapFieldsRecursively(transform: (fieldNode: FieldNode) -> T): List<T> {
    val result = mutableListOf<T>()
    forEachFieldsRecursively { node ->
        result.add(transform(node))
    }
    return result
}

class FieldNode(
    val field: Field,
    val parent: FieldNode? = null
) {
    val children: List<FieldNode>
        get() {
            return if(TypeUtils.isSimpleType(this.field.type)) {
                emptyList()
            } else {
                this.field.type.declaredFields.reversed().map { FieldNode(it, this) }
            }
        }

    operator fun component1(): Field {
        return field
    }

    operator fun component2(): FieldNode? {
        return parent
    }
}