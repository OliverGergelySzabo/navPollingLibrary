package com.github.oliverszabo.navpolling.polling.dto

import com.github.oliverszabo.navpolling.util.base64Encode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class QueryInvoiceDataResponseTest {
    private val uncompressedData = Paths.get("src", "test", "resources", "gzip", "UncompressedFile.txt")
        .toFile()
        .readText()
    private val base64EncodedUncompressedData = base64Encode(uncompressedData)
    private val base64EncodedCompressedData = base64Encode(
        Paths.get("src", "test", "resources", "gzip", "CompressedFile.gz").toFile().readBytes()
    )

    @Test
    fun `If InvoiceDataResult is null then invoiceDataXml is null`() {
        assertNull(createResponse(null).invoiceDataXml)
    }

    @Test
    fun `If invoice data is gzipped then it is unzipped and base64 decoded to the invoiceDataXml`() {
        assertEquals(uncompressedData, createResponse(base64EncodedCompressedData).invoiceDataXml)
    }

    @Test
    fun `If invoice data is not gzipped then it is base64 decoded to the invoiceDataXml`() {
        assertEquals(uncompressedData, createResponse(base64EncodedUncompressedData).invoiceDataXml)
    }

    private fun createResponse(invoiceData: String?): QueryInvoiceDataResponse {
        if(invoiceData == null) {
            return QueryInvoiceDataResponse(null)
        }
        return QueryInvoiceDataResponse(QueryInvoiceDataResponse.InvoiceDataResult(invoiceData))
    }
}