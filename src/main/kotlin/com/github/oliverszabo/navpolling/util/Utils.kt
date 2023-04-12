package com.github.oliverszabo.navpolling.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.security.MessageDigest
import java.time.Instant

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
    return this.digest(s.toByteArray()).map { byteToHex(it) }.joinToString(separator = "")
}

fun Instant.plusDays(daysToAdd: Long): Instant {
    return plusSeconds(86400 * daysToAdd)
}