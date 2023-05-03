package com.github.oliverszabo.navpolling.api

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.util.ErrorMessages
import com.github.oliverszabo.navpolling.util.assertThrownException
import com.github.oliverszabo.navpolling.util.minusDays
import com.github.oliverszabo.navpolling.util.plusDays
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.time.Instant

class InvoiceFeedTest {
    inner class TestFeed(pastFetchingPeriod: Int? = null): InvoiceFeed(pastFetchingPeriod) {
        override fun loadState(): State? {
            return loadedState
        }

        override fun loadUsers(): Set<TechnicalUser> {
            return loadedUsers
        }
    }

    inner class TestFeedWithSaveMethods: InvoiceFeed(1) {
        override fun saveState(feedState: State) {
            assertEquals(expectedSavedState, feedState)
        }

        override fun saveUsers(users: Set<TechnicalUser>) {
            assertEquals(expectedSavedUsers, users)
        }
    }

    class TestFeedWithoutOverrodeMethods: InvoiceFeed(1)

    private val now = Instant.now()
    private var loadedState: InvoiceFeed.State? = null
    private var loadedUsers = emptySet<TechnicalUser>()
    private var expectedSavedState: InvoiceFeed.State? = null
    private var expectedSavedUsers = emptySet<TechnicalUser>()

    private val librarySettings = mockk<LibrarySettings>()

    @BeforeEach
    fun beforeEach() {
        mockkStatic(Instant::class)
        every { Instant.now() } returns now
        every { librarySettings.defaultPastFetchingPeriod } returns 15
    }

    @AfterEach
    fun afterEach() {
        loadedState = null
        loadedUsers = emptySet()
    }

    @Test
    fun constructorThrowsExceptionIfThePastFetchingPeriodIsNegative() {
        assertThrownException<NavPollingLibraryInitializationException>(
            ErrorMessages.paramMustBeGreaterThanOrEqualTo("pastFetchingPeriod", 0)
        ) {
            createInvoiceFeed(-1)
        }
    }

    @Test
    fun ifNoPastFetchingPeriodIsDefinedThenTheDefaultPastFetchingPeriodIsReturnedByGetPastFetchingPeriod() {
        assertEquals(librarySettings.defaultPastFetchingPeriod, createInvoiceFeed().getPastFetchingPeriod())
    }

    @Test
    fun ifPastFetchingPeriodIsDefinedThenItIsReturnedByGetPastFetchingPeriod() {
        val period = 5
        assertEquals(period, createInvoiceFeed(period).getPastFetchingPeriod())
    }

    @Test
    fun initLoadsStateAndUsersAndStartsFeed() {
        val technicalUser = createTechnicalUser("login")
        val newAddedTechnicalUser = createTechnicalUser("newlyAdded")

        loadedState = InvoiceFeed.State(now.minusDays(20), setOf(newAddedTechnicalUser.login))
        loadedUsers = setOf(technicalUser, newAddedTechnicalUser)

        val feed = createInvoiceFeed()
        assertFalse(feed.isRunning())
        feed.init()

        assertTrue(feed.isRunning())
        assertEquals(loadedUsers, feed.getUsers())
        assertEquals(loadedState!!.pollingCompleteUntil, feed.getPollingCompleteUntil())
        assertEquals(setOf(newAddedTechnicalUser), feed.getUsers(true))
        assertEquals(setOf(technicalUser), feed.getUsers(false))
    }

    @Test
    fun ifNoStateAndUsersSpecifiedInitLoadsDefaultValues() {
        val feed = TestFeedWithoutOverrodeMethods()
        assertFalse(feed.isRunning())
        feed.init()

        assertTrue(feed.isRunning())
        assertEquals(emptySet<TechnicalUser>(), feed.getUsers())
        assertEquals(now, feed.getPollingCompleteUntil())
        assertEquals(emptySet<TechnicalUser>(), feed.getUsers(true))
    }

    @Test
    fun addUserRemoveUserAndClearUsersWorkAsExpected() {
        val technicalUserToRemoveLogin = "toRemove"
        val technicalUser = createTechnicalUser("login")
        val technicalUserToRemove = createTechnicalUser(technicalUserToRemoveLogin)
        val technicalUserToAdd = createTechnicalUser("toAdd")

        loadedUsers = setOf(technicalUser, technicalUserToRemove)
        val feed = createAndInitInvoiceFeed()

        feed.addUser(technicalUserToAdd)
        assertUsers(setOf(technicalUser, technicalUserToRemove), setOf(technicalUserToAdd), feed)

        feed.removeUser(technicalUserToRemoveLogin)
        assertUsers(setOf(technicalUser), setOf(technicalUserToAdd), feed)

        feed.removeUser(technicalUserToAdd)
        assertUsers(setOf(technicalUser), emptySet(), feed)

        feed.clearUsers()
        assertUsers(emptySet(), emptySet(), feed)
    }

    @Test
    fun startAndStopWorkAsExpected() {
        val feed = createInvoiceFeed()
        assertFalse(feed.isRunning())

        feed.start()
        assertTrue(feed.isRunning())

        feed.stop()
        assertFalse(feed.isRunning())
    }

    @Test
    fun destroySavesStateAndUsersAndStopsFeed() {
        val technicalUserToAddLogin = "toAdd"
        val technicalUserToAdd = createTechnicalUser(technicalUserToAddLogin)
        val expectedPollingCompleteUntil = now.plusDays(10)

        val feed = TestFeedWithSaveMethods()
        feed.init()
        assertTrue(feed.isRunning())
        feed.addUser(technicalUserToAdd)
        feed.setPollingCompleteUntil(expectedPollingCompleteUntil)

        expectedSavedState = InvoiceFeed.State(expectedPollingCompleteUntil, setOf(technicalUserToAddLogin))
        expectedSavedUsers = setOf(technicalUserToAdd)
        feed.destroy()
        assertFalse(feed.isRunning())
    }

    @Test
    fun onPastFetchingCompletedRemovesUsersFromTheSetOfUsersRequiringPastFetching() {
        val technicalUserToAdd = createTechnicalUser("toAdd")
        val feed = createAndInitInvoiceFeed()

        feed.addUser(technicalUserToAdd)
        assertUsers(emptySet(), setOf(technicalUserToAdd), feed)

        feed.onPastFetchingCompleted(setOf(technicalUserToAdd))
        assertUsers(setOf(technicalUserToAdd), emptySet(), feed)
    }

    @Test
    fun setPollingCompleteUntilWorkAsExpected() {
        val expectedPollingCompleteUntil = now.plusDays(17)
        val feed = createAndInitInvoiceFeed()
        assertEquals(now, feed.getPollingCompleteUntil())

        feed.setPollingCompleteUntil(expectedPollingCompleteUntil)
        assertEquals(expectedPollingCompleteUntil, feed.getPollingCompleteUntil())
    }

    private fun createInvoiceFeed(pastFetchingPeriod: Int? = null): TestFeed {
        val feed = TestFeed(pastFetchingPeriod)
        val librarySettingsField = InvoiceFeed::class.java.getDeclaredField("librarySettings")
        librarySettingsField.trySetAccessible()
        librarySettingsField.set(feed, librarySettings)
        val postConstructMethod = InvoiceFeed::class.java.getDeclaredMethod("postConstruct")
        Method::class.java.getDeclaredField("modifiers").apply {
            trySetAccessible()
            setInt(postConstructMethod, postConstructMethod.modifiers and Modifier.FINAL.inv())
        }
        postConstructMethod.trySetAccessible()
        postConstructMethod.invoke(feed)
        return feed
    }

    private fun createAndInitInvoiceFeed(pastFetchingPeriod: Int? = null): TestFeed {
        return createInvoiceFeed(pastFetchingPeriod).apply { init() }
    }

    private fun createTechnicalUser(login: String): TechnicalUser {
        return TechnicalUser(login, "p", "t", "s")
    }

    private fun assertUsers(users: Set<TechnicalUser>, newlyAddedUsers: Set<TechnicalUser>, feed: InvoiceFeed) {
        assertEquals(users + newlyAddedUsers, feed.getUsers())
        assertEquals(newlyAddedUsers, feed.getUsers(true))
        assertEquals(users, feed.getUsers(false))
    }
}