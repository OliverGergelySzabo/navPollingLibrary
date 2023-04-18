package com.github.oliverszabo.navpolling.config

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.util.ErrorMessages
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
    }

    val pollingPoolSize: Int = initializeThreadPoolSize(PropertyNames.POLLING_POOL_SIZE, DefaultValues.pollingPoolSize)
    val connectionPoolSize: Int = initializeThreadPoolSize(PropertyNames.CONNECTION_POOL_SIZE, DefaultValues.connectionPoolSize)
    val pollingFrequency: Trigger = initializePollingFrequency()
    val defaultPastFetchingPeriod: Int = initializeDefaultPastFetchingPeriod()
    val shutdownTimeout: Long = initializeShutdownTimeout()

    private fun initializePollingFrequency(): Trigger {
        val specifiedPollingFrequency = environment.getProperty(PropertyNames.POLLING_FREQUENCY)
        if(specifiedPollingFrequency.isNullOrBlank()) {
            return DefaultValues.pollingFrequency
        }

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
        val specifiedDefaultPastFetchingPeriod = environment.getProperty<Int?>(PropertyNames.DEFAULT_PAST_FETCHING_PERIOD)
            ?: DefaultValues.defaultPastFetchingPeriod
        if(specifiedDefaultPastFetchingPeriod < 0) {
            throw NavPollingLibraryInitializationException(
                ErrorMessages.propertyMustBeGreaterThanOrEqualTo(PropertyNames.DEFAULT_PAST_FETCHING_PERIOD, 0)
            )
        }
        return specifiedDefaultPastFetchingPeriod
    }

    private fun initializeThreadPoolSize(propertyName: String, defaultSize: Int): Int {
        val specifiedPoolSize = environment.getProperty<Int?>(propertyName) ?: defaultSize
        if(specifiedPoolSize < 1) {
            throw NavPollingLibraryInitializationException(
                ErrorMessages.propertyMustBeGreaterThan(propertyName, 0)
            )
        }
        return specifiedPoolSize
    }

    private fun initializeShutdownTimeout(): Long {
        val specifiedShutdownTimeout = environment.getProperty<Long?>(PropertyNames.SHUTDOWN_TIMEOUT) ?: DefaultValues.shutdownTimeout
        if(specifiedShutdownTimeout < 0) {
            throw NavPollingLibraryInitializationException(
                ErrorMessages.propertyMustBeGreaterThanOrEqualTo(PropertyNames.SHUTDOWN_TIMEOUT, 0)
            )
        }
        return specifiedShutdownTimeout
    }

    class PropertyNames private constructor() {
        companion object {
            const val POLLING_POOL_SIZE = "nav-polling.polling-pool-size"
            const val CONNECTION_POOL_SIZE = "nav-polling.connection-pool-size"
            const val POLLING_FREQUENCY = "nav-polling.polling-frequency"
            const val DEFAULT_PAST_FETCHING_PERIOD = "nav-polling.default-past-fetching-period"
            const val SHUTDOWN_TIMEOUT = "nav-polling.shutdown-timeout"
        }
    }
    class DefaultValues private constructor() {
        companion object {
            const val pollingPoolSize = 5
            const val connectionPoolSize = 10
            val pollingFrequency = PeriodicTrigger(1, TimeUnit.DAYS)
            const val defaultPastFetchingPeriod = 0
            const val shutdownTimeout = 10L
        }
    }
}