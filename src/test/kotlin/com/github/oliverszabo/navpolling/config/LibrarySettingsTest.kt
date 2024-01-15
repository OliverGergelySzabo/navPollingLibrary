package com.github.oliverszabo.navpolling.config

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.config.LibrarySettings.Companion.INVALID_POLLING_FREQUENCY_ERROR
import com.github.oliverszabo.navpolling.config.LibrarySettings.Companion.INVALID_SOFTWARE_DEV_COUNTRY_CODE
import com.github.oliverszabo.navpolling.config.LibrarySettings.Companion.INVALID_SOFTWARE_ID
import com.github.oliverszabo.navpolling.config.LibrarySettings.Companion.MANDATORY_PROPERTY_MISSING_ERROR_TEMPLATE
import com.github.oliverszabo.navpolling.config.LibrarySettings.Companion.POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR
import com.github.oliverszabo.navpolling.config.LibrarySettings.PropertyNames
import com.github.oliverszabo.navpolling.config.LibrarySettings.PropertyNames.ConsumerSoftwareInfo
import com.github.oliverszabo.navpolling.config.LibrarySettings.DefaultValues
import com.github.oliverszabo.navpolling.polling.dto.Software
import com.github.oliverszabo.navpolling.util.ErrorMessages
import io.mockk.MockKMatcherScope
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.core.env.Environment
import org.springframework.core.env.getProperty
import org.springframework.scheduling.support.CronTrigger
import org.springframework.scheduling.support.PeriodicTrigger
import java.util.concurrent.TimeUnit

class LibrarySettingsTest {
    private val environment = mockk<Environment>(relaxed = true)
    private val consumerSoftwareInfoMandatoryStringProperties = listOf(
        ConsumerSoftwareInfo.SOFTWARE_NAME,
        ConsumerSoftwareInfo.SOFTWARE_MAIN_VERSION,
        ConsumerSoftwareInfo.SOFTWARE_DEV_NAME,
        ConsumerSoftwareInfo.SOFTWARE_DEV_CONTACT
    )
    private val dummyStringPropertyValue = "some value"

    @BeforeEach
    fun beforeEach() {
        every { environment.getProperty<Int>(any(), any()) } returns null
        every { environment.getProperty(any()) } returns null
        every { environment.getProperty<Boolean>(any()) } returns null
        every { environment.getProperty(anyOf(consumerSoftwareInfoMandatoryStringProperties)) } returns dummyStringPropertyValue
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_ID) } returns "ABCDEFGH-123456789"
        every {
            environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_OPERATION)
        } returns Software.SoftwareOperation.LOCAL_SOFTWARE.name
    }

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Test
    fun `if pollingPoolSize is not specified then the default value is returned`() {
        val settings = createLibrarySettings()
        assertEquals(DefaultValues.pollingPoolSize, settings.pollingPoolSize)
    }

    @Test
    fun `if pollingPoolSize is specified then the correct value is returned`() {
        val expectedSize = DefaultValues.pollingPoolSize + 5
        every { environment.getProperty<Int?>(PropertyNames.POLLING_POOL_SIZE, any()) } returns expectedSize
        val settings = createLibrarySettings()
        assertEquals(expectedSize, settings.pollingPoolSize)
    }

    @Test
    fun `if pollingPoolSize less than 1 is specified then the correct error is returned`() {
        every { environment.getProperty<Int?>(PropertyNames.POLLING_POOL_SIZE, any()) } returns 0
        createLibrarySettingsAndAssertException(
            ErrorMessages.propertyMustBeGreaterThan(PropertyNames.POLLING_POOL_SIZE, 0)
        )
    }

    @Test
    fun `if connectionPoolSize is not specified then the default value is returned`() {
        val settings = createLibrarySettings()
        assertEquals(DefaultValues.connectionPoolSize, settings.connectionPoolSize)
    }

    @Test
    fun `if connectionPoolSize is specified then the correct value is returned`() {
        val expectedSize = DefaultValues.pollingPoolSize + 5
        every { environment.getProperty<Int?>(PropertyNames.CONNECTION_POOL_SIZE, any()) } returns expectedSize
        val settings = createLibrarySettings()
        assertEquals(expectedSize, settings.connectionPoolSize)
    }

    @Test
    fun `if connectionPoolSize less than 1 is specified then the correct error is returned`() {
        every { environment.getProperty<Int?>(PropertyNames.CONNECTION_POOL_SIZE, any()) } returns 0
        createLibrarySettingsAndAssertException(
            ErrorMessages.propertyMustBeGreaterThan(PropertyNames.CONNECTION_POOL_SIZE, 0)
        )
    }

    @Test
    fun `if defaultPastFetchingPeriod is not specified then the default value is returned`() {
        val settings = createLibrarySettings()
        assertEquals(DefaultValues.defaultPastFetchingPeriod, settings.defaultPastFetchingPeriod)
    }

    @Test
    fun `if defaultPastFetchingPeriod is specified then the correct value is returned`() {
        val expectedDefaultPastFetchingPeriod = DefaultValues.defaultPastFetchingPeriod + 5
        every { environment.getProperty<Int?>(PropertyNames.DEFAULT_PAST_FETCHING_PERIOD, any()) } returns expectedDefaultPastFetchingPeriod
        val settings = createLibrarySettings()
        assertEquals(expectedDefaultPastFetchingPeriod, settings.defaultPastFetchingPeriod)

    }

    @Test
    fun `if defaultPastFetchingPeriod less than 0 is specified then the correct error is returned`() {
        every { environment.getProperty<Int?>(PropertyNames.DEFAULT_PAST_FETCHING_PERIOD, any()) } returns -1
        createLibrarySettingsAndAssertException(
            ErrorMessages.propertyMustBeGreaterThanOrEqualTo(PropertyNames.DEFAULT_PAST_FETCHING_PERIOD, 0)
        )
    }

    @Test
    fun `if pollingFrequency is not specified then the default value is returned`() {
        val settings = createLibrarySettings()
        assertEquals(DefaultValues.pollingFrequency, settings.pollingFrequency)
    }

    @Test
    fun `if periodic pollingFrequency is specified then the correct value is returned`() {
        var expectedTrigger = PeriodicTrigger(2, TimeUnit.HOURS)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "2 HOURS"
        var settings = createLibrarySettings()
        assertEquals(expectedTrigger, settings.pollingFrequency)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "2   HOURS"
        settings = createLibrarySettings()
        assertEquals(expectedTrigger, settings.pollingFrequency)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "  2 HOURS         "
        settings = createLibrarySettings()
        assertEquals(expectedTrigger, settings.pollingFrequency)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "   2      HOURS         "
        settings = createLibrarySettings()
        assertEquals(expectedTrigger, settings.pollingFrequency)

        expectedTrigger = PeriodicTrigger(1, TimeUnit.MINUTES)
        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "1 MINUTES"
        settings = createLibrarySettings()
        assertEquals(expectedTrigger, settings.pollingFrequency)
    }

    @Test
    fun `if cron pollingFrequency is specified then the correct value is returned`() {
        var cron = "0 3 0 * * *"
        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns cron
        var settings = createLibrarySettings()
        assertEquals(CronTrigger(cron), settings.pollingFrequency)

        cron = "2 * * * * *"
        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns cron
        settings = createLibrarySettings()
        assertEquals(CronTrigger(cron), settings.pollingFrequency)
    }

    @Test
    fun `if invalid pollingFrequency is specified then the correct error is returned`() {
        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns ""
        createLibrarySettingsAndAssertException(INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "        "
        createLibrarySettingsAndAssertException(INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "2 PERC"
        createLibrarySettingsAndAssertException(INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "asd PERC"
        createLibrarySettingsAndAssertException(INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "daslkjasfj"
        createLibrarySettingsAndAssertException(INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "212312312"
        createLibrarySettingsAndAssertException(INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "0 3 e * * *"
        createLibrarySettingsAndAssertException(INVALID_POLLING_FREQUENCY_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "60 3 * * * *"
        createLibrarySettingsAndAssertException(INVALID_POLLING_FREQUENCY_ERROR)
    }

    @Test
    fun `if pollingFrequency less than 1 minute is specified then the correct error is returned`() {
        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "30 SECONDS"
        createLibrarySettingsAndAssertException(POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "59 SECONDS"
        createLibrarySettingsAndAssertException(POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "30 SECONDS"
        createLibrarySettingsAndAssertException(POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "*/10 * * * * *"
        createLibrarySettingsAndAssertException(POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "1,59 * * * * *"
        createLibrarySettingsAndAssertException(POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)

        every { environment.getProperty(PropertyNames.POLLING_FREQUENCY) } returns "*/59 * * * * *"
        createLibrarySettingsAndAssertException(POLLING_FREQUENCY_IS_LESS_THAN_1_MIN_ERROR)
    }

    @Test
    fun `if shutdownTimeout is not specified then the default value is returned`() {
        val settings = createLibrarySettings()
        assertEquals(DefaultValues.shutdownTimeout, settings.shutdownTimeout)
    }

    @Test
    fun `if shutdownTimeout is specified then the correct value is returned`() {
        val expectedTimeout = DefaultValues.shutdownTimeout + 5
        every { environment.getProperty<Int?>(PropertyNames.SHUTDOWN_TIMEOUT, any()) } returns expectedTimeout
        val settings = createLibrarySettings()
        assertEquals(expectedTimeout, settings.shutdownTimeout)
    }

    @Test
    fun `if shutdownTimeout less than 0 is specified then the correct error is returned`() {
        every { environment.getProperty<Int?>(PropertyNames.SHUTDOWN_TIMEOUT, any()) } returns -1
        createLibrarySettingsAndAssertException(
            ErrorMessages.propertyMustBeGreaterThanOrEqualTo(PropertyNames.SHUTDOWN_TIMEOUT, 0)
        )
    }

    @Test
    fun `if requestTimeout is not specified then the default value is returned`() {
        val settings = createLibrarySettings()
        assertEquals(DefaultValues.requestTimeout, settings.requestTimeout)
    }

    @Test
    fun `if requestTimeout is specified then the correct value is returned`() {
        val expectedTimeout = DefaultValues.requestTimeout + 5000
        every { environment.getProperty<Int?>(PropertyNames.REQUEST_TIMEOUT, any()) } returns expectedTimeout
        val settings = createLibrarySettings()
        assertEquals(expectedTimeout, settings.requestTimeout)
    }

    @Test
    fun `if requestTimeout less than 1000 is specified then the correct error is returned`() {
        every { environment.getProperty<Int?>(PropertyNames.REQUEST_TIMEOUT, any()) } returns 999
        createLibrarySettingsAndAssertException(
            ErrorMessages.propertyMustBeGreaterThanOrEqualTo(PropertyNames.REQUEST_TIMEOUT, 1000)
        )
    }

    @Test
    fun `if saveUsersAfterPolling is not specified then the default value is returned`() {
        assertEquals(DefaultValues.saveUsersAfterPolling, createLibrarySettings().saveUsersAfterPolling)
    }

    @Test
    fun `if saveUsersAfterPolling is specified then the correct value is returned`() {
        val expectedSaveStateAfterPolling = false
        every { environment.getProperty<Boolean?>(PropertyNames.SAVE_USERS_AFTER_POLLING) } returns expectedSaveStateAfterPolling
        assertEquals(expectedSaveStateAfterPolling, createLibrarySettings().saveUsersAfterPolling)
    }

    @ValueSource(
        strings = [
            ConsumerSoftwareInfo.SOFTWARE_ID,
            ConsumerSoftwareInfo.SOFTWARE_NAME,
            ConsumerSoftwareInfo.SOFTWARE_MAIN_VERSION,
            ConsumerSoftwareInfo.SOFTWARE_DEV_NAME,
            ConsumerSoftwareInfo.SOFTWARE_DEV_CONTACT
        ]
    )
    @ParameterizedTest
    fun `if any of the mandatory string properties of the consumer software configuration are null or blank then correct error is returned`(testedProperty: String) {
        val otherProperties = consumerSoftwareInfoMandatoryStringProperties.filter { it != testedProperty }
        val expectedErrorMessage = MANDATORY_PROPERTY_MISSING_ERROR_TEMPLATE.format(testedProperty)
        every { environment.getProperty(anyOf(otherProperties)) } returns dummyStringPropertyValue

        every { environment.getProperty(testedProperty) } returns null
        createLibrarySettingsAndAssertException(expectedErrorMessage)

        every { environment.getProperty(testedProperty) } returns ""
        createLibrarySettingsAndAssertException(expectedErrorMessage)
    }

    @Test
    fun `if the specified software id is invalid then correct error is returned`() {
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_ID) } returns "DOT.NOT.ALLOWED123"
        createLibrarySettingsAndAssertException(INVALID_SOFTWARE_ID)

        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_ID) } returns "LONGERTHAN18CHARACTERS"
        createLibrarySettingsAndAssertException(INVALID_SOFTWARE_ID)

        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_ID) } returns "LoWERCASE123456789"
        createLibrarySettingsAndAssertException(INVALID_SOFTWARE_ID)
    }

    @Test
    fun `if the specified software dev country code is invalid then correct error is returned`() {
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_COUNTRY_CODE) } returns "Hu"
        createLibrarySettingsAndAssertException(INVALID_SOFTWARE_DEV_COUNTRY_CODE)

        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_COUNTRY_CODE) } returns "ÜA"
        createLibrarySettingsAndAssertException(INVALID_SOFTWARE_DEV_COUNTRY_CODE)

        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_COUNTRY_CODE) } returns "GBR"
        createLibrarySettingsAndAssertException(INVALID_SOFTWARE_DEV_COUNTRY_CODE)
    }

    @Test
    fun `if all of required the properties of the consumer software configuration are specified then the correct consumer software info object is returned`() {
        val softwareInfo = Software(
            softwareId = "AZ27917882-3241244",
            softwareName = "softwareName",
            softwareOperation = Software.SoftwareOperation.ONLINE_SERVICE,
            softwareMainVersion = "softwareMainVersion",
            softwareDevName = "softwareDevName",
            softwareDevContact = "softwareDevContact"
        )

        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_ID) } returns softwareInfo.softwareId
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_NAME) } returns softwareInfo.softwareName
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_OPERATION) } returns softwareInfo.softwareOperation.name
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_MAIN_VERSION) } returns softwareInfo.softwareMainVersion
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_NAME) } returns softwareInfo.softwareDevName
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_CONTACT) } returns softwareInfo.softwareDevContact
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_COUNTRY_CODE) } returns softwareInfo.softwareDevCountryCode
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_TAX_NUMBER) } returns softwareInfo.softwareDevTaxNumber

        assertThat(createLibrarySettings().consumerSoftwareInfo).usingRecursiveComparison().isEqualTo(softwareInfo)
    }

    @Test
    fun `if all of the properties of the consumer software configuration are specified then the correct consumer software info object is returned`() {
        val softwareInfo = Software(
            softwareId = "AZ27917882-3241244",
            softwareName = "softwareName",
            softwareOperation = Software.SoftwareOperation.ONLINE_SERVICE,
            softwareMainVersion = "softwareMainVersion",
            softwareDevName = "softwareDevName",
            softwareDevContact = "softwareDevContact",
            softwareDevCountryCode = "HU",
            softwareDevTaxNumber = "softwareDevTaxNumber",
        )

        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_ID) } returns softwareInfo.softwareId
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_NAME) } returns softwareInfo.softwareName
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_OPERATION) } returns softwareInfo.softwareOperation.name
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_MAIN_VERSION) } returns softwareInfo.softwareMainVersion
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_NAME) } returns softwareInfo.softwareDevName
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_CONTACT) } returns softwareInfo.softwareDevContact
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_COUNTRY_CODE) } returns softwareInfo.softwareDevCountryCode
        every { environment.getProperty(ConsumerSoftwareInfo.SOFTWARE_DEV_TAX_NUMBER) } returns softwareInfo.softwareDevTaxNumber

        assertThat(createLibrarySettings().consumerSoftwareInfo).usingRecursiveComparison().isEqualTo(softwareInfo)
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

    private fun MockKMatcherScope.anyOf(elements: List<String>) = match<String> {
        elements.contains(it)
    }
}