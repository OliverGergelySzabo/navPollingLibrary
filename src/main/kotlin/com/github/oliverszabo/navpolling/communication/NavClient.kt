package com.github.oliverszabo.navpolling.communication

import com.github.oliverszabo.navpolling.communication.dto.*
import com.github.oliverszabo.navpolling.util.createXmlMapper
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NavClient(private val softwareInfo: Software) {
    companion object{
        private val TEST_URL = "https://api-test.onlineszamla.nav.gov.hu/invoiceService/v3"
        private val PROD_URL = "https://api.onlineszamla.nav.gov.hu/invoiceService/v3"

        private val xmlMapper = createXmlMapper()
        private val log = LoggerFactory.getLogger(NavClient::class.java)

        private val navTrustManager = arrayOf<TrustManager>(
            object: X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }

                override fun checkClientTrusted(
                    certs: Array<X509Certificate>, authType: String
                ) {
                }

                override fun checkServerTrusted(
                    certs: Array<X509Certificate>, authType: String
                ) {
                }
            }
        )
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .version(HttpClient.Version.HTTP_2)
        .sslContext(SSLContext.getInstance("SSL").apply { init(null, navTrustManager, SecureRandom()) })
        .build()

    suspend fun <T: RequestBase, R> query(technicalUser: NavTechnicalUser, request: T, resultClass: Class<R>): R {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$PROD_URL/${request.command}"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/xml")
            .header("Accept", "*/*")
            .POST(HttpRequest.BodyPublishers.ofString(request.getXml(Config(technicalUser, softwareInfo))))
            .build()

        try {
            val httpResponse = httpClient.sendAsync(httpRequest, BodyHandlers.ofString()).await()
            if(httpResponse.statusCode() != 200) {
                val navError = xmlMapper.readValue(httpResponse.body(), ErrorResponse::class.java).result
                //todo: throw proper exception
                throw Exception(navError?.funcCode ?: ""/*, result?.errorCode ?: "", result?.message ?: ""*/)
            }
            return xmlMapper.readValue(httpResponse.body(), resultClass)
        } catch (e: SocketException) {
            //in case NAV service is not available or other connection errors
            log.warn("A NAV connection error occurred, message: ${e.message}")
            //todo: throw proper exception
            throw Exception("navConnectionError")
        }
    }
}