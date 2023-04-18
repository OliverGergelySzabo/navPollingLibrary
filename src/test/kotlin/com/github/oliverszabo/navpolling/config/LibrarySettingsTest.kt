package com.github.oliverszabo.navpolling.config

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.util.ErrorMessages
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.env.Environment
import org.springframework.scheduling.support.CronTrigger
import org.springframework.scheduling.support.PeriodicTrigger
import java.util.concurrent.TimeUnit

class LibrarySettingsTest {
    private val environment = mockk<Environment>(relaxed = true)

    @BeforeEach
    fun beforeEach() {
        every { environment.getProperty<Int?>(any(), any()) } returns null
        every { environment.getProperty<Long?>(any(), any()) } returns null
        every { environment.getProperty(any()) } returns null
    }

    @Test
    fun ifPollingPoolSizeIsNotSpecifiedThenTheDefaultValueIsReturned() {
        val settings = createLibrarySettings()
        assertEquals(LibrarySettings.DefaultValues.pollingPoolSize, settings.pollingPoolSize)
    }

    @Test
    fun ifPollingPoolSizeIsSpecifiedThenTheCorrectValueIsReturned() {
        val expectedSize = LibrarySettings.DefaultValues.pollingPoolSize + 5
        every { environment.getProperty<Int?>(LibrarySettings.PropertyNames.POLLING_POOL_SIZE, any()) } returns expectedSize
        val settings = createLibrarySettings()
        assertEquals(expectedSize, settings.pollingPoolSize)
    }

    @Test
    fun ifPollingPoolSizeBelowOneIsSpecifiedThenTheCorrectErrorIsReturned() {
        every { environment.getProperty<Int?>(LibrarySettings.PropertyNames.POLLING_POOL_SIZE, any()) } returns 0
        createLibrarySettingsAndAssertException(
            ErrorMessages.propertyMustBeGreaterThan(LibrarySettings.PropertyNames.POLLING_POOL_SIZE, 0)
        )
    }

    @Test
    fun ifConnectionPoolSizeIsNotSpecifiedThenTheDefaultValueIsReturned() {
        val settings = createLibrarySettings()
        assertEquals(LibrarySettings.DefaultValues.connectionPoolSize, settings.connectionPoolSize)
    }

    @Test
    fun ifConnectionPoolSizeIsSpecifiedThenTheCorrectValueIsReturned() {
        val expectedSize = LibrarySettings.DefaultValues.pollingPoolSize + 5
        every { environment.getProperty<Int?>(LibrarySettings.PropertyNames.CONNECTION_POOL_SIZE, any()) } returns expectedSize
        val settings = createLibrarySettings()
        assertEquals(expectedSize, settings.connectionPoolSize)
    }

    @Test
    fun ifConnectionPoolSizeBelowOneIsSpecifiedThenTheCorrectErrorIsReturned() {
        every { environment.getProperty<Int?>(LibrarySettings.PropertyNames.CONNECTION_POOL_SIZE, any()) } returns 0
        createLibrarySettingsAndAssertException(
            ErrorMessages.propertyMustBeGreaterThan(LibrarySettings.PropertyNames.CONNECTION_POOL_SIZE, 0)
        )
    }

    @Test
    fun ifDefaultPastFetchingPeriodIsNotSpecifiedThenTheDefaultValueIsReturned() {
        val settings = createLibrarySettings()
        assertEquals(LibrarySettings.DefaultValues.defaultPastFetchingPeriod, settings.defaultPastFetchingPeriod)
    }

    @Test
    fun ifDefaultPastFetchingPeriodIsSpecifiedThenTheCorrectValueIsReturned() {
        val expectedDefaultPastFetchingPeriod = LibrarySettings.DefaultValues.defaultPastFetchingPeriod + 5
        every { environment.getProperty<Int?>(LibrarySettings.PropertyNames.DEFAULT_PAST_FETCHING_PERIOD, any()) } returns expectedDefaultPastFetchingPeriod
        val settings = createLibrarySettings()
        assertEquals(expectedDefaultPastFetchingPeriod, settings.defaultPastFetchingPeriod)

    }

    @Test
    fun ifDefaultPastFetchingPeriodBelowZeroIsSpecifiedThenTheCorrectErrorIsReturned() {
        every { environment.getProperty<Int?>(LibrarySettings.PropertyNames.DEFAULT_PAST_FETCHING_PERIOD, any()) } returns -1
        createLibrarySettingsAndAssertException(
            ErrorMessages.propertyMustBeGreaterThanOrEqualTo(LibrarySettings.PropertyNames.DEFAULT_PAST_FETCHING_PERIOD, 0)
        )
    }

    @Test
    fun ifPollingFrequencyIsNotSpecifiedThenTheDefaultValueIsReturned() {
        val settings = createLibrarySettings()
        assertEquals(LibrarySettings.DefaultValues.pollingFrequency, settings.pollingFrequency)
    }

    @Test
    fun ifPeriodicPollingFrequencyIsSpecifiedThenTheCorrectValueIsReturned() {
        var expectedTrigger = PeriodicTrigger(2, TimeUnit.HOURS)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "2 HOURS"
        var settings = createLibrarySettings()
        assertEquals(expectedTrigger, settings.pollingFrequency)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "2   HOURS"
        settings = createLibrarySettings()
        assertEquals(expectedTrigger, settings.pollingFrequency)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "  2 HOURS         "
        settings = createLibrarySettings()
        assertEquals(expectedTrigger, settings.pollingFrequency)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "   2      HOURS         "
        settings = createLibrarySettings()
        assertEquals(expectedTrigger, settings.pollingFrequency)

        expectedTrigger = PeriodicTrigger(1, TimeUnit.MINUTES)
        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "1 MINUTES"
        settings = createLibrarySettings()
        assertEquals(expectedTrigger, settings.pollingFrequency)
    }

    @Test
    fun ifCronPollingFrequencyIsSpecifiedThenTheCorrectValueIsReturned() {
        var cron = "0 3 0 * * *"
        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns cron
        var settings = createLibrarySettings()
        assertEquals(CronTrigger(cron), settings.pollingFrequency)

        cron = "2 * * * * *"
        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns cron
        settings = createLibrarySettings()
        assertEquals(CronTrigger(cron), settings.pollingFrequency)
    }

    @Test
    fun ifInvalidPollingFrequencyIsSpecifiedThenTheCorrectErrorIsReturned() {
        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "2 PERC"
        createLibrarySettingsAndAssertException(LibrarySettings.INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "daslkjasfj"
        createLibrarySettingsAndAssertException(LibrarySettings.INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "212312312"
        createLibrarySettingsAndAssertException(LibrarySettings.INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "0 3 e * * *"
        createLibrarySettingsAndAssertException(LibrarySettings.INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "60 3 * * * *"
        createLibrarySettingsAndAssertException(LibrarySettings.INVALID_POLLING_FREQUENCY_ERROR)
    }

    @Test
    fun ifInvalidPollingFrequencyLessThanOneMinuteIsSpecifiedThenTheCorrectErrorIsReturned() {
        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "30 SECONDS"
        createLibrarySettingsAndAssertException(LibrarySettings.POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "59 SECONDS"
        createLibrarySettingsAndAssertException(LibrarySettings.POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "30 SECONDS"
        createLibrarySettingsAndAssertException(LibrarySettings.POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "*/10 * * * * *"
        createLibrarySettingsAndAssertException(LibrarySettings.POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "1,59 * * * * *"
        createLibrarySettingsAndAssertException(LibrarySettings.POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)

        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns "*/59 * * * * *"
        createLibrarySettingsAndAssertException(LibrarySettings.POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)
    }

    @Test
    fun ifShutdownTimeoutIsNotSpecifiedThenTheDefaultValueIsReturned() {
        val settings = createLibrarySettings()
        assertEquals(LibrarySettings.DefaultValues.shutdownTimeout, settings.shutdownTimeout)
    }

    @Test
    fun ifShutdownTimeoutIsSpecifiedThenTheCorrectValueIsReturned() {
        val expectedTimeout = LibrarySettings.DefaultValues.shutdownTimeout + 5
        every { environment.getProperty<Long?>(LibrarySettings.PropertyNames.SHUTDOWN_TIMEOUT, any()) } returns expectedTimeout
        val settings = createLibrarySettings()
        assertEquals(expectedTimeout, settings.shutdownTimeout)
    }

    @Test
    fun ifShutdownTimeoutBelowZeroIsSpecifiedThenTheCorrectErrorIsReturned() {
        every { environment.getProperty<Long?>(LibrarySettings.PropertyNames.SHUTDOWN_TIMEOUT, any()) } returns -1
        createLibrarySettingsAndAssertException(
            ErrorMessages.propertyMustBeGreaterThanOrEqualTo(LibrarySettings.PropertyNames.SHUTDOWN_TIMEOUT, 0)
        )
    }

    private fun createLibrarySettings(): LibrarySettings {
        return LibrarySettings(environment)
    }

    private fun createLibrarySettingsAndAssertException(expectedMessage: String) {
        val exception =  assertThrows<NavPollingLibraryInitializationException> {
            createLibrarySettings()
        }
        assertEquals(expectedMessage, exception.message)
    }
}