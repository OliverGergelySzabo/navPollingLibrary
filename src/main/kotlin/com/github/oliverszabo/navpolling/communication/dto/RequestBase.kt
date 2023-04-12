package com.github.oliverszabo.navpolling.communication.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import com.github.oliverszabo.navpolling.util.hashString
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

abstract class RequestBase {
    companion object {
        private val API_NS = "http://schemas.nav.gov.hu/OSA/3.0/api";
        private val COMMON_NS = "http://schemas.nav.gov.hu/NTCA/1.0/common";

        private fun generateRequestId() : String{
            return (UUID.randomUUID().toString() + UUID.randomUUID().toString()).filter { it.isLetterOrDigit() }.substring(0,30)
        }

        private fun generateRequestSignature(requestId : String, timestamp: Instant, sigKey: String) : String {
            val signatureTimeFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("UTC"))
            return MessageDigest.getInstance("SHA3-512").hashString(requestId + signatureTimeFormat.format(timestamp) + sigKey)
        }

        val xmlMapper = XmlMapper().apply {
            setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            enable(SerializationFeature.INDENT_OUTPUT)
            configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
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

    protected data class SoftwareData(
        val softwareId : String,
        val softwareName : String,
        val softwareOperation : String,
        val softwareMainVersion : String,
        val softwareDevName : String,
        val softwareDevContact : String,
        val softwareDevCountryCode : String? = null,
        val softwareDevTaxNumber : String? = null,
    )

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
        @field:JacksonXmlProperty(localName = "software")
        val softwareData: SoftwareData

        init {
            val now = Instant.now()
            val requestId = generateRequestId()
            this.header = Header(
                requestId = requestId,
                timestamp = now.truncatedTo(ChronoUnit.MILLIS).toString()
            )
            this.userData = UserData(
                login = config.user.login,
                passwordHash = UserData.HashString(config.user.passwordHash, "SHA-512"),
                taxNumber = config.user.taxNumber,
                requestSignature = UserData.HashString(
                    generateRequestSignature(requestId, now, config.user.sigKey),
                    "SHA3-512"
                )
            )
            this.softwareData = SoftwareData(
                config.software.softwareId,
                config.software.softwareName,
                config.software.softwareOperation.toString(),
                config.software.softwareMainVersion,
                config.software.softwareDevName,
                config.software.softwareDevContact,
                config.software.softwareDevCountryCode,
                config.software.softwareDevTaxNumber
            )
        }
    }

    protected fun generateXml(payload: Any): String {
        var ret = xmlMapper.writeValueAsString(payload)
        val rootName = ret.split('<','>').filter { !it.matches("\\s*".toRegex()) } [1]
        //inserting namespaces
        ret = ret.replaceFirst(rootName, """$rootName xmlns:common="$COMMON_NS" xmlns="$API_NS"""")
        return ret
    }

    abstract val command: String

    abstract fun getXml(config: Config): String
}