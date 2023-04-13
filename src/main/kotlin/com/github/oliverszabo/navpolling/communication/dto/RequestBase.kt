package com.github.oliverszabo.navpolling.communication.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.oliverszabo.navpolling.util.hashString
import com.github.oliverszabo.navpolling.util.randomHex
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

abstract class RequestBase(val config: Config) {
    companion object {
        private const val API_NS = "http://schemas.nav.gov.hu/OSA/3.0/api"
        private const val COMMON_NS = "http://schemas.nav.gov.hu/NTCA/1.0/common"
        private val secureRandom = SecureRandom()
        private val xmlMapper = XmlMapper().apply {
            setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            enable(SerializationFeature.INDENT_OUTPUT)
            configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
            registerKotlinModule()
            registerModule(JavaTimeModule())
        }

        private fun generateRequestSignature(requestId : String, timestamp: Instant, sigKey: String) : String {
            val signatureTimeFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("UTC"))
            return MessageDigest.getInstance("SHA3-512").hashString(requestId + signatureTimeFormat.format(timestamp) + sigKey)
        }
    }

    protected data class Header(
        @field:JacksonXmlProperty(localName = "common:requestId")
        val requestId : String,
        @field:JacksonXmlProperty(localName = "common:timestamp")
        val timestamp : String,
        @field:JacksonXmlProperty(localName = "common:requestVersion")
        val requestVersion : String = "3.0",
        @field:JacksonXmlProperty(localName = "common:headerVersion")
        val headerVersion : String = "1.0",
    )

    protected data class UserData(
        @field:JacksonXmlProperty(localName = "common:login")
        val login : String,
        @field:JacksonXmlProperty(localName = "common:passwordHash")
        val passwordHash : HashString,
        @field:JacksonXmlProperty(localName = "common:taxNumber")
        val taxNumber : String,
        @field:JacksonXmlProperty(localName = "common:requestSignature")
        val requestSignature : HashString,
    ){
        data class HashString(
            @field:JacksonXmlText
            val value : String,
            @field:JacksonXmlProperty(isAttribute = true)
            val cryptoType : String
        )
    }

    protected data class RelationalQueryParam(
        val queryOperator: QueryOperator,
        val queryValue: String
    )

    @JsonPropertyOrder("common:header","common:user","software")
    protected open class RootBase(config: Config) {
        @field:JacksonXmlProperty(localName = "common:header")
        val header: Header
        @field:JacksonXmlProperty(localName = "common:user")
        val userData: UserData
        val software: Software

        init {
            val now = Instant.now()
            val requestId = secureRandom.randomHex(30)

            header = Header(
                requestId = requestId,
                timestamp = now.truncatedTo(ChronoUnit.MILLIS).toString()
            )
            userData = UserData(
                login = config.user.login,
                passwordHash = UserData.HashString(config.user.passwordHash, "SHA-512"),
                taxNumber = config.user.taxNumber,
                requestSignature = UserData.HashString(
                    generateRequestSignature(requestId, now, config.user.sigKey),
                    "SHA3-512"
                )
            )
            this.software = config.software
        }
    }

    protected fun generateXml(payload: Any): String {
        val xmlWithoutNamespaces = xmlMapper.writeValueAsString(payload)
        val rootName = xmlWithoutNamespaces.split('<','>').filter { !it.matches("\\s*".toRegex()) }[1]
        //inserting namespaces
        //println(xmlWithoutNamespaces.replaceFirst(rootName, """$rootName xmlns:common="$COMMON_NS" xmlns="$API_NS""""))
        return xmlWithoutNamespaces.replaceFirst(rootName, """$rootName xmlns:common="$COMMON_NS" xmlns="$API_NS"""")
    }

    abstract val command: String

    abstract fun toXml(): String
}