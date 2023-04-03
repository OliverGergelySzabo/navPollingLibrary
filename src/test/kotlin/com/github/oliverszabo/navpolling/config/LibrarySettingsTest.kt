package com.github.oliverszabo.navpolling.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment
import org.springframework.scheduling.support.CronTrigger
import org.springframework.scheduling.support.PeriodicTrigger
import java.util.concurrent.TimeUnit

class LibrarySettingsTest {
    private val environment = mockk<Environment>(relaxed = true)

    @BeforeEach
    fun beforeEach() {
        every { environment.getProperty<Int?>(any(), any()) } returns null
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
    fun ifPollingFrequencyIsNotSpecifiedThenTheDefaultValueIsReturned() {
        val settings = createLibrarySettings()
        assertEquals(LibrarySettings.DefaultValues.pollingFrequency, settings.pollingFrequency)
    }

    @Test
    fun ifPeriodicPollingFrequencyIsSpecifiedThenTheCorrectValueIsReturned() {
        val expectedTrigger = PeriodicTrigger(2, TimeUnit.HOURS)

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
    }

    @Test
    fun ifCronPollingFrequencyIsSpecifiedThenTheCorrectValueIsReturned() {
        val cron = "0 3 0 * * *"
        every { environment.getProperty(LibrarySettings.PropertyNames.POLLING_FREQUENCY) } returns cron
        val settings = createLibrarySettings()
        assertEquals(CronTrigger(cron), settings.pollingFrequency)
    }

    private fun createLibrarySettings(): LibrarySettings {
        return LibrarySettings(environment)
    }
}