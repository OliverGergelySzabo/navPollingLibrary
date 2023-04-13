package com.github.oliverszabo.navpolling.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.TemporalUnit

fun byteToHex(b: Byte) : String{
    val ret = b.toUByte().toString(16).uppercase()
    return if(ret.length == 1) "0$ret" else ret
}

fun createXmlMapper(): XmlMapper {
    return XmlMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        registerKotlinModule()
        registerModule(JavaTimeModule())
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