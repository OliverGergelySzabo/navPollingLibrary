package com.github.oliverszabo.navpolling.polling

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.exception.NavInvoiceServiceConnectionException
import com.github.oliverszabo.navpolling.api.exception.NavQueryException
import com.github.oliverszabo.navpolling.polling.dto.*
import com.github.oliverszabo.navpolling.util.NavDataCreator
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.SocketException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

class NavClientTest {
    private val requestTimeout = 5000
    private val clientBuilder = mockk<HttpClient.Builder>(relaxed = true)
    private val requestBuilder = mockk<HttpRequest.Builder>(relaxed = true)
    private val httpClient = mockk<HttpClient>(relaxed = true)
    private val httpRequest = mockk<HttpRequest>(relaxed = true)
    private val httpResponse = mockk<HttpResponse<String>>(relaxed = true)

    private val request = QueryInvoiceDataRequest(
        config =  Config(
            NavTechnicalUser("l", "p", "t", "s"),
            Software("", "", Software.SoftwareOperation.LOCAL_SOFTWARE, "", "", "")
        ),
        invoiceNumber = "invoiceNumber",
        invoiceDirection = InvoiceDirection.OUTBOUND,
        supplierTaxNumber = null
    )
    private val funcCode = "ERROR"
    private val errorCode = "INVALID_SECURITY_USER"
    private val errorMessage = "Helytelen authentikációs adatok"

    private val invoiceData = "ladskjglkfdjglkafj"
    private val navResponse = NavDataCreator.createQueryInvoiceDataResponse(invoiceData)

    @BeforeEach
    fun beforeEach() {
        mockkStatic(HttpClient::class)
        every { HttpClient.newBuilder() } returns clientBuilder
        every {
            clientBuilder.connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_2).build()
        } returns httpClient

        mockkStatic(HttpRequest::class)
        every { HttpRequest.newBuilder() } returns requestBuilder
        every {
            HttpRequest.newBuilder()
                .uri(URI.create("${NavClient.PROD_API_URL}/queryInvoiceData"))
                .timeout(Duration.ofMillis(requestTimeout.toLong()))
                .header("Content-Type", "application/xml")
                .header("Accept", "*/*")
                .POST(any())
                .build()
        } returns httpRequest

        every {
            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
        } returns CompletableFuture.completedFuture(httpResponse)
    }

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Test
    fun `client throws NavInvoiceServiceConnectionException if the NAV server cannot be reached`() {
        val socketException = SocketException()
        every { httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()) } throws socketException

        val client = createClient()
        val exception = assertThrows<NavInvoiceServiceConnectionException> {
            runBlocking {
                client.query(request, QueryInvoiceDataResponse::class.java)
            }
        }
        assertEquals(socketException, exception.cause)
    }

    @Test
    fun `client throws NavQueryException when the NAV server returns an error`() {
        every { httpResponse.body() } returns NavDataCreator.createErrorResponse(funcCode, errorCode, errorMessage)
        every { httpResponse.statusCode() } returns 400

        val client = createClient()
        val exception = assertThrows<NavQueryException> {
            runBlocking {
                client.query(request, QueryInvoiceDataResponse::class.java)
            }
        }
        assertEquals(funcCode, exception.funcCode)
        assertEquals(errorCode, exception.errorCode)
        assertEquals(errorMessage, exception.message)
    }

    @Test
    fun `client returns correct parsed response`() {
        every { httpResponse.body() } returns navResponse
        every { httpResponse.statusCode() } returns 200

        runBlocking {
            val client = createClient()
            val parsedResponse = client.query(request, QueryInvoiceDataResponse::class.java)
            assertEquals(invoiceData, parsedResponse.invoiceDataXml)
        }
    }

    private fun createClient(): NavClient {
        return NavClient(requestTimeout)
    }
}