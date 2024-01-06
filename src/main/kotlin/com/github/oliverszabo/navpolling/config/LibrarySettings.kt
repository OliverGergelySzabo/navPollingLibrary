package com.github.oliverszabo.navpolling.config

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.polling.dto.Software
import com.github.oliverszabo.navpolling.util.ErrorMessages
import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.env.Environment
import org.springframework.core.env.getProperty
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.support.CronTrigger
import org.springframework.scheduling.support.PeriodicTrigger
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class LibrarySettings(
    private val environment: Environment
) {
    companion object {
        const val INVALID_POLLING_FREQUENCY_ERROR =
            "The polling frequency specified in '${PropertyNames.POLLING_FREQUENCY}' is not a valid duration or cron expression."
        const val POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR =
            "The polling frequency specified in '${PropertyNames.POLLING_FREQUENCY}' cannot have a period less than 1 minute."
        const val MANDATORY_PROPERTY_MISSING_ERROR_TEMPLATE =
            "A value for the configuration property '%s' must be specified"
        const val INVALID_PROPERTY_TYPE_ERROR_TEMPLATE =
            "The value specified for the configuration property '%s' must be of type '%s'"
        const val INVALID_ENUM_PROPERTY_ERROR_TEMPLATE =
            "The value specified for the configuration property '%s' must have one of the following values: %s"
        const val INVALID_SOFTWARE_ID =
            "The software id specified in ${PropertyNames.ConsumerSoftwareInfo.SOFTWARE_ID} is not a valid software id " +
            "(valid software ids must match the following regex: '[0-9A-Z\\-]{18}'"
        const val INVALID_SOFTWARE_DEV_COUNTRY_CODE =
            "The software dev country code specified in ${PropertyNames.ConsumerSoftwareInfo.SOFTWARE_DEV_COUNTRY_CODE} is not a valid country code"
    }

    val pollingPoolSize = initializeThreadPoolSize(PropertyNames.POLLING_POOL_SIZE, DefaultValues.pollingPoolSize)
    val connectionPoolSize = initializeThreadPoolSize(PropertyNames.CONNECTION_POOL_SIZE, DefaultValues.connectionPoolSize)
    val pollingFrequency = initializePollingFrequency()
    val defaultPastFetchingPeriod = initializeDefaultPastFetchingPeriod()
    val shutdownTimeout = initializeShutdownTimeout()
    val requestTimeout= initializeRequestTimeout()
    val passwordHashingRequired = initializeBoolean(PropertyNames.PASSWORD_HASHING_REQUIRED, DefaultValues.passwordHashingRequired)
    val saveUsersAfterPolling = initializeBoolean(PropertyNames.SAVE_USERS_AFTER_POLLING, DefaultValues.saveUsersAfterPolling)
    val consumerSoftwareInfo = Software(
        softwareId = initializeSoftwareId(),
        softwareName = initializeString(PropertyNames.ConsumerSoftwareInfo.SOFTWARE_NAME),
        softwareOperation = initializeSoftwareOperation(),
        softwareMainVersion = initializeString(PropertyNames.ConsumerSoftwareInfo.SOFTWARE_MAIN_VERSION),
        softwareDevName = initializeString(PropertyNames.ConsumerSoftwareInfo.SOFTWARE_DEV_NAME),
        softwareDevContact = initializeString(PropertyNames.ConsumerSoftwareInfo.SOFTWARE_DEV_CONTACT),
        softwareDevCountryCode = initializeSoftwareDevCountryCode(),
        softwareDevTaxNumber = environment.getProperty(PropertyNames.ConsumerSoftwareInfo.SOFTWARE_DEV_TAX_NUMBER),
    )

    private fun initializePollingFrequency(): Trigger {
        val specifiedPollingFrequency = environment.getProperty(PropertyNames.POLLING_FREQUENCY)
            ?: return DefaultValues.pollingFrequency

        val specifiedPollingFrequencyParts = specifiedPollingFrequency.trim().split("\\s+".toRegex())
        if(specifiedPollingFrequencyParts.size == 2
            && specifiedPollingFrequencyParts[0].all { it.isDigit() }
            && TimeUnit.values().map { it.name }.contains(specifiedPollingFrequencyParts[1])
        ) {
            val specifiedPeriod = specifiedPollingFrequencyParts[0].toLong()
            val specifiedTimeUnit = TimeUnit.valueOf(specifiedPollingFrequencyParts[1])
            if(Duration.of(specifiedPeriod, specifiedTimeUnit.toChronoUnit()) < Duration.ofMinutes(1)) {
                throw NavPollingLibraryInitializationException(POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)
            }
            return PeriodicTrigger(specifiedPeriod, specifiedTimeUnit)
        }

        try {
            val cronTrigger = CronTrigger(specifiedPollingFrequency)
            // the if is after parsing, so we know that the specifiedPollingFrequency is a valid cron expression
            // each cron expression with a frequency less than a minute must contain a non digit character in the seconds part
            if(specifiedPollingFrequency.trim().split("\\s+".toRegex()).first().any { !it.isDigit() }) {
                throw NavPollingLibraryInitializationException(POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)
            }
            return cronTrigger
        } catch (ex: IllegalArgumentException) {
            throw NavPollingLibraryInitializationException(INVALID_POLLING_FREQUENCY_ERROR, ex)
        }
    }

    private fun initializeDefaultPastFetchingPeriod(): Int {
        val specifiedDefaultPastFetchingPeriod = getProperty(PropertyNames.DEFAULT_PAST_FETCHING_PERIOD)
            ?: DefaultValues.defaultPastFetchingPeriod
        if(specifiedDefaultPastFetchingPeriod < 0) {
            throw NavPollingLibraryInitializationException(
                ErrorMessages.propertyMustBeGreaterThanOrEqualTo(PropertyNames.DEFAULT_PAST_FETCHING_PERIOD, 0)
            )
        }
        return specifiedDefaultPastFetchingPeriod
    }

    private fun initializeThreadPoolSize(propertyName: String, defaultSize: Int): Int {
        val specifiedPoolSize = getProperty(propertyName) ?: defaultSize
        if(specifiedPoolSize < 1) {
            throw NavPollingLibraryInitializationException(
                ErrorMessages.propertyMustBeGreaterThan(propertyName, 0)
            )
        }
        return specifiedPoolSize
    }

    private fun initializeShutdownTimeout(): Int {
        val specifiedShutdownTimeout = getProperty(PropertyNames.SHUTDOWN_TIMEOUT) ?: DefaultValues.shutdownTimeout
        if(specifiedShutdownTimeout < 0) {
            throw NavPollingLibraryInitializationException(
                ErrorMessages.propertyMustBeGreaterThanOrEqualTo(PropertyNames.SHUTDOWN_TIMEOUT, 0)
            )
        }
        return specifiedShutdownTimeout
    }

    private fun initializeRequestTimeout(): Int {
        val specifiedRequestTimeout = getProperty(PropertyNames.REQUEST_TIMEOUT) ?: DefaultValues.requestTimeout
        //TODO: more validation
        if(specifiedRequestTimeout < 1000) {
            throw NavPollingLibraryInitializationException(
                ErrorMessages.propertyMustBeGreaterThanOrEqualTo(PropertyNames.REQUEST_TIMEOUT, 1000)
            )
        }
        return specifiedRequestTimeout
    }

    private fun initializeBoolean(propertyName: String, defaultValue: Boolean): Boolean {
         return getProperty(propertyName) ?: defaultValue
    }
    
    private fun initializeString(propertyName: String): String {
        val specifiedValue = environment.getProperty(propertyName)
        if(specifiedValue.isNullOrBlank()) {
            throw NavPollingLibraryInitializationException(MANDATORY_PROPERTY_MISSING_ERROR_TEMPLATE.format(propertyName))
        }
        return specifiedValue
    }

    private fun initializeSoftwareId(): String {
        val specifiedSoftwareId = initializeString(PropertyNames.ConsumerSoftwareInfo.SOFTWARE_ID)
        if(!specifiedSoftwareId.matches("[0-9A-Z\\-]{18}".toRegex())) {
            throw NavPollingLibraryInitializationException(INVALID_SOFTWARE_ID)
        }
        return specifiedSoftwareId
    }

    private fun initializeSoftwareOperation(): Software.SoftwareOperation {
        val propertyName = PropertyNames.ConsumerSoftwareInfo.SOFTWARE_OPERATION
        val specifiedSoftwareOperationString = initializeString(propertyName)
        try {
            return Software.SoftwareOperation.valueOf(specifiedSoftwareOperationString)
        } catch (e: IllegalArgumentException) {
            throw NavPollingLibraryInitializationException(
                INVALID_ENUM_PROPERTY_ERROR_TEMPLATE.format(
                    propertyName,
                    Software.SoftwareOperation.values().joinToString { it.name }
                )
            )
        }
    }

    private fun initializeSoftwareDevCountryCode(): String? {
        val specifiedCountryCode = environment.getProperty(PropertyNames.ConsumerSoftwareInfo.SOFTWARE_DEV_COUNTRY_CODE)
        if(specifiedCountryCode != null && !specifiedCountryCode.matches("[A-Z]{2}".toRegex())) {
            throw NavPollingLibraryInitializationException(INVALID_SOFTWARE_DEV_COUNTRY_CODE)
        }
        return specifiedCountryCode
    }

    private inline fun <reified T> getProperty(propertyName: String): T? {
        try {
            return environment.getProperty<T>(propertyName)
        } catch (e: ConversionFailedException) {
            throw NavPollingLibraryInitializationException(
                INVALID_PROPERTY_TYPE_ERROR_TEMPLATE.format(propertyName, T::class.java.simpleName)
            )
        }
    }

    object PropertyNames {
        const val POLLING_POOL_SIZE = "nav-polling.polling-pool-size"
        const val CONNECTION_POOL_SIZE = "nav-polling.connection-pool-size"
        const val POLLING_FREQUENCY = "nav-polling.polling-frequency"
        const val DEFAULT_PAST_FETCHING_PERIOD = "nav-polling.default-past-fetching-period"
        const val SHUTDOWN_TIMEOUT = "nav-polling.shutdown-timeout"
        const val REQUEST_TIMEOUT = "nav-polling.request-timeout"
        const val PASSWORD_HASHING_REQUIRED = "nav-polling.password-hashing-required"
        const val SAVE_USERS_AFTER_POLLING = "nav-polling.save-users-after-polling"

        object ConsumerSoftwareInfo {
            const val SOFTWARE_ID = "nav-polling.consumer-software-info.software-id"
            const val SOFTWARE_NAME = "nav-polling.consumer-software-info.software-name"
            const val SOFTWARE_OPERATION = "nav-polling.consumer-software-info.software-operation"
            const val SOFTWARE_MAIN_VERSION =  "nav-polling.consumer-software-info.software-main-version"
            const val SOFTWARE_DEV_NAME =  "nav-polling.consumer-software-info.software-dev-name"
            const val SOFTWARE_DEV_CONTACT =  "nav-polling.consumer-software-info.software-dev-contact"
            const val SOFTWARE_DEV_COUNTRY_CODE =  "nav-polling.consumer-software-info.software-dev-country-code"
            const val SOFTWARE_DEV_TAX_NUMBER =  "nav-polling.consumer-software-info.software-dev-tax-number"
        }
    }
    object DefaultValues {
        const val pollingPoolSize = 5
        const val connectionPoolSize = 10
        val pollingFrequency = PeriodicTrigger(1, TimeUnit.HOURS)
        const val defaultPastFetchingPeriod = 0
        const val shutdownTimeout = 10
        const val requestTimeout = 5000
        const val passwordHashingRequired = false
        const val saveUsersAfterPolling = true
    }
}