package com.github.oliverszabo.navpolling.polling.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

class ErrorResponse {
    @field:JacksonXmlProperty(localName = "result")
    var result : Result? = null

    class Result {
        var funcCode : String? = null
        var errorCode : String? = null
        var message : String? = null
    }
}