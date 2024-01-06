package com.github.oliverszabo.navpolling.polling.dto

import com.github.oliverszabo.navpolling.util.randomHex
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class NavRequestTest {

    private val software = Software(
        "softwareId",
        "softwareName",
        Software.SoftwareOperation.LOCAL_SOFTWARE,
        "softwareMainVersion",
        "softwareDevName",
        "softwareDevContact"
    )
    private val technicalUser = NavTechnicalUser("login", "passwordHash", "taxNumber", "sigKey")
    private val config = Config(technicalUser, software)

    private val now = Instant.EPOCH
    private val expectedXml = """
        <?xml version='1.0' encoding='UTF-8'?>
        <RootBase xmlns:common="http://schemas.nav.gov.hu/NTCA/1.0/common" xmlns="http://schemas.nav.gov.hu/OSA/3.0/api">
          <common:header>
            <common:requestId>A4D8CDFBA5D1599C88A62962307BBE</common:requestId>
            <common:timestamp>1970-01-01T00:00:00Z</common:timestamp>
            <common:requestVersion>3.0</common:requestVersion>
            <common:headerVersion>1.0</common:headerVersion>
          </common:header>
          <common:user>
            <common:login>${technicalUser.login}</common:login>
            <common:passwordHash cryptoType="SHA-512">${technicalUser.passwordHash}</common:passwordHash>
            <common:taxNumber>${technicalUser.taxNumber}</common:taxNumber>
            <common:requestSignature cryptoType="SHA3-512">39A69A92A7846DBB89B0B057F4D17ED61E691EADBB3137CD65273C2A9E2A127DC71BB8984BC770DBF3F23F42A2C9A7334D60C0E72C5AF3CE67374FE4C2E07691</common:requestSignature>
          </common:user>
          <software>
            <softwareId>${software.softwareId}</softwareId>
            <softwareName>${software.softwareName}</softwareName>
            <softwareOperation>${software.softwareOperation}</softwareOperation>
            <softwareMainVersion>${software.softwareMainVersion}</softwareMainVersion>
            <softwareDevName>${software.softwareDevName}</softwareDevName>
            <softwareDevContact>${software.softwareDevContact}</softwareDevContact>
          </software>
        </RootBase>
    """.replace("\\s".toRegex(), "")

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Test
    fun `NavRequest creates basic request fields and request signature correctly`() {
        mockkStatic(::randomHex)
        every { randomHex(NavRequest.REQUEST_ID_LENGTH) } returns "A4D8CDFBA5D1599C88A62962307BBE"
        mockkStatic(Instant::class)
        every { Instant.now() } returns now
        assertEquals(expectedXml, SimpleNavRequest(config).toXml().replace("\\s".toRegex(), ""))
    }

    private class SimpleNavRequest(config: Config): NavRequest(config) {
        override val operation = "queryInvoiceData"

        override fun toXml(): String {
            return generateXml(RootBase(config))
        }
    }
}
