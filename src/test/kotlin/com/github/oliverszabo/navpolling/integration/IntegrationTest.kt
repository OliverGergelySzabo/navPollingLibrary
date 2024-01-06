package com.github.oliverszabo.navpolling.integration

import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.polling.NavClient
import com.github.oliverszabo.navpolling.util.NavDataCreator
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

class IntegrationTest {
    private val clientBuilder = mockk<HttpClient.Builder>(relaxed = true)
    private val requestBuilder = mockk<HttpRequest.Builder>(relaxed = true)
    private val httpClient = mockk<HttpClient>(relaxed = true)
    private val digestRequest = mockk<HttpRequest>(relaxed = true)
    private val digestResponse = mockk<HttpResponse<String>>(relaxed = true)
    private val dataRequest = mockk<HttpRequest>(relaxed = true)
    private val dataResponse = mockk<HttpResponse<String>>(relaxed = true)

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Test
    fun `library correctly polls the NAV API, converts invoices to the specified format and then publishes invoice arrived events`() {
        mockHttpCalls()
        mockInstantNow()

        val applicationContext = AnnotationConfigApplicationContext("com.github.oliverszabo.navpolling")
        IntegrationTestHelper.waitForTestCompletion()

        // needed for calling Lifecycle bean shutdown
        applicationContext.stop()
        // needed so @PreDestroy is called on MockInvoiceFeed
        applicationContext.close()
    }

    private fun mockHttpCalls() {
        mockkStatic(HttpClient::class)
        every { HttpClient.newBuilder() } returns clientBuilder
        every {
            clientBuilder.connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_2).build()
        } returns httpClient

        mockkStatic(HttpRequest::class)
        every { HttpRequest.newBuilder() } returns requestBuilder

        val timeout = Duration.ofMillis(LibrarySettings.DefaultValues.requestTimeout.toLong())
        every {
            HttpRequest.newBuilder()
                .uri(URI.create("${NavClient.PROD_API_URL}/queryInvoiceDigest"))
                .timeout(timeout)
                .header("Content-Type", "application/xml")
                .header("Accept", "*/*")
                .POST(any())
                .build()
        } returns digestRequest
        every {
            httpClient.sendAsync(digestRequest, HttpResponse.BodyHandlers.ofString())
        } returns CompletableFuture.completedFuture(digestResponse)
        every { digestResponse.statusCode() } returns 200
        every { digestResponse.body() } returns
                NavDataCreator.createQueryInvoiceDigestResponse(
                    IntegrationTestConstants.invoiceNumber,
                    IntegrationTestConstants.supplierTaxNumber
                )

        every {
            HttpRequest.newBuilder()
                .uri(URI.create("${NavClient.PROD_API_URL}/queryInvoiceData"))
                .timeout(timeout)
                .header("Content-Type", "application/xml")
                .header("Accept", "*/*")
                .POST(any())
                .build()
        } returns dataRequest
        every {
            httpClient.sendAsync(dataRequest, HttpResponse.BodyHandlers.ofString())
        } returns CompletableFuture.completedFuture(dataResponse)
        every { dataResponse.statusCode() } returns 200
        every { dataResponse.body() } returns
                NavDataCreator.createQueryInvoiceDataResponse(
                    IntegrationTestConstants.invoiceNumber,
                    IntegrationTestConstants.supplierTaxNumber
                )
    }

    private fun mockInstantNow() {
        mockkStatic(Instant::class)
        every { Instant.now() } returns IntegrationTestConstants.now
    }
}