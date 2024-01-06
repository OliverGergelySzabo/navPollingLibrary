package com.github.oliverszabo.navpolling.polling.dto

data class Software(
    val softwareId : String,
    val softwareName : String,
    val softwareOperation : SoftwareOperation,
    val softwareMainVersion : String,
    val softwareDevName : String,
    val softwareDevContact : String,
    val softwareDevCountryCode : String? = null,
    val softwareDevTaxNumber : String? = null,
) {
    enum class SoftwareOperation {
        LOCAL_SOFTWARE, ONLINE_SERVICE
    }
}