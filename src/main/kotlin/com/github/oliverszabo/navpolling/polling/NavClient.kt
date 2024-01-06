package com.github.oliverszabo.navpolling.polling

import com.github.oliverszabo.navpolling.api.exception.NavInvoiceServiceConnectionException
import com.github.oliverszabo.navpolling.api.exception.NavQueryException
import com.github.oliverszabo.navpolling.polling.dto.*
import com.github.oliverszabo.navpolling.util.createXmlMapper
import kotlinx.coroutines.future.await
import java.net.SocketException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

class NavClient(
    val requestTimeout: Int
) {
    companion object{
        const val TEST_API_URL = "https://api-test.onlineszamla.nav.gov.hu/invoiceService/v3"
        const val PROD_API_URL = "https://api.onlineszamla.nav.gov.hu/invoiceService/v3"

        private val xmlMapper = createXmlMapper()
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .version(HttpClient.Version.HTTP_2)
        .build()

    suspend fun <T: NavRequest, R> query(request: T, resultClass: Class<R>): R {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$PROD_API_URL/${request.operation}"))
            .timeout(Duration.ofMillis(requestTimeout.toLong()))
            .header("Content-Type", "application/xml")
            .header("Accept", "*/*")
            .POST(HttpRequest.BodyPublishers.ofString(request.toXml()))
            .build()

        try {
            val httpResponse = httpClient.sendAsync(httpRequest, BodyHandlers.ofString()).await()
            if(httpResponse.statusCode() != 200) {
                val navError = xmlMapper.readValue(httpResponse.body(), ErrorResponse::class.java).result
                throw NavQueryException(navError?.funcCode, navError?.errorCode, navError?.message)
            }
            return xmlMapper.readValue(httpResponse.body(), resultClass)
        } catch (e: SocketException) {
            throw NavInvoiceServiceConnectionException(e)
        }
    }
}