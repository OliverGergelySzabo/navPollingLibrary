package com.github.oliverszabo.navpolling.api

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.util.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.time.Instant

class InvoiceFeedTest {
    inner class TestFeed(pastFetchingPeriod: Int? = null): InvoiceFeed(pastFetchingPeriod) {
        override fun loadUsers(): Set<TechnicalUser> {
            return loadedUsers
        }
    }

    inner class TestFeedWithSaveMethods: InvoiceFeed(1) {
        override fun saveUsers(users: Set<TechnicalUser>) {
            assertUsers(expectedSavedUsers, users)
        }
    }

    class TestFeedWithoutOverrodeMethods: InvoiceFeed(1)

    private val now = Instant.now()
    private var loadedUsers = emptySet<TechnicalUser>()
    private var expectedSavedUsers = emptySet<TechnicalUser>()

    private val librarySettings = mockk<LibrarySettings>()
    private val currentTimeProvider = mockk<CurrentTimeProvider>()

    @BeforeEach
    fun beforeEach() {
        every { currentTimeProvider.currentSecond() } returns now
        every { librarySettings.defaultPastFetchingPeriod } returns 15
    }

    @AfterEach
    fun afterEach() {
        loadedUsers = emptySet()
    }

    @Test
    fun `constructor throws exception if the past fetching period is negative`() {
        assertThrownException<NavPollingLibraryInitializationException>(
            ErrorMessages.paramMustBeGreaterThanOrEqualTo("pastFetchingPeriod", 0)
        ) {
            createInvoiceFeed(-1)
        }
    }

    @Test
    fun `if no past fetching period is defined then the default past fetching period is returned by getPastFetchingPeriod`() {
        assertEquals(librarySettings.defaultPastFetchingPeriod, createInvoiceFeed().getPastFetchingPeriod())
    }

    @Test
    fun `if past fetching period is defined then it is returned by getPastFetchingPeriod`() {
        val period = 5
        assertEquals(period, createInvoiceFeed(period).getPastFetchingPeriod())
    }

    @Test
    fun `init loads users with and without state and starts feed`() {
        val user = createTechnicalUser("login")
        val otherUser = createTechnicalUser("withState", now.minusDays(20))

        loadedUsers = setOf(user, otherUser)

        val feed = createInvoiceFeed()
        assertFalse(feed.isRunning())
        feed.init()

        assertTrue(feed.isRunning())
        assertUsers(
            setOf(user.withPollingCompleteUntil(now), otherUser),
            feed.getUsers()
        )
    }

    @Test
    fun `if no state and users specified init loads default values`() {
        val feed = mockAutowiredFieldsAndRunPostConstruct(TestFeedWithoutOverrodeMethods())
        assertFalse(feed.isRunning())
        feed.init()

        assertTrue(feed.isRunning())
        assertEquals(emptySet<TechnicalUser>(), feed.getUsers())
    }

    @Test
    fun `addUser removeUser and clearUsers work as expected`() {
        val userToRemoveLogin = "toRemove"
        val user = createTechnicalUser("login", Instant.now().minusDays(25))
        val userToRemove = createTechnicalUser(userToRemoveLogin, Instant.now().minusDays(30))
        val userToAdd = createTechnicalUser("toAdd")
        val otherUserToAdd = createTechnicalUser("otherToAdd", now.minusDays(14))

        loadedUsers = setOf(user, userToRemove)
        val feed = createAndInitInvoiceFeed(1)

        feed.addUser(userToAdd)
        assertUsers(setOf(user, userToRemove, userToAdd.withPollingCompleteUntil(now.minusDays(1))), feed.getUsers())
        feed.addUser(otherUserToAdd)
        assertUsers(setOf(user, userToRemove, userToAdd.withPollingCompleteUntil(now.minusDays(1)), otherUserToAdd), feed.getUsers())

        val foundUser = feed.getUser(userToRemoveLogin)
        assertNotNull(foundUser)
        assertTrue(isSameTechnicalUser(userToRemove, foundUser!!))
        assertNull(feed.getUser("notExistingLogin"))

        feed.removeUser(userToRemoveLogin)
        assertUsers(setOf(user, userToAdd.withPollingCompleteUntil(now.minusDays(1)), otherUserToAdd), feed.getUsers())

        feed.removeUser(userToAdd)
        assertUsers(setOf(user, otherUserToAdd), feed.getUsers())
        feed.removeUser(otherUserToAdd)
        assertUsers(setOf(user), feed.getUsers())

        feed.clearUsers()
        assertUsers(emptySet(), feed.getUsers())
    }

    @Test
    fun `getUser and updateUser work as expected`() {
        val user = createTechnicalUser("login", now.minusDays(25))
        loadedUsers = setOf(user)
        val feed = createInvoiceFeed()
        feed.init()

        var foundUser = feed.getUser(user.login)
        assertNotNull(foundUser)
        assertTrue(isSameTechnicalUser(user, foundUser!!))
        assertNull(feed.getUser("notExistingLogin"))

        val updatedUser = user.withPollingDirections(setOf(InvoiceDirection.INBOUND)).withPollingCompleteUntil(now.plusDays(1))
        feed.updateUser(updatedUser)
        foundUser = feed.getUser(user.login)
        assertTrue(isSameTechnicalUser(updatedUser, foundUser!!))

        // updateUser should not put new user into the feed
        val userNotInFeed = createTechnicalUser("noInFeed")
        feed.updateUser(userNotInFeed)
        assertEquals(setOf(updatedUser), feed.getUsers())
    }

    @Test
    fun `setPollingCompleteUntilForUsers works as expected`() {
        val userWillBeUpdated = createTechnicalUser("updated")
        val userWillBeRemovedBeforeUpdate = createTechnicalUser("removeBeforeUpdate")
        val willBeModifiedBeforeUpdateLogin = "modifiedBeforeUpdate"
        val userWillBeModifiedBeforeUpdate = createTechnicalUser(willBeModifiedBeforeUpdateLogin)
        loadedUsers = setOf(userWillBeUpdated, userWillBeModifiedBeforeUpdate, userWillBeRemovedBeforeUpdate)
        val expectedUpdatedPollingCompleteUntil = now.plusDays(1)
        val expectedModifiedPollingCompleteUntil = now.minusDays(30)

        val feed = createInvoiceFeed()
        feed.init()
        val usersToUpdate = feed.getUsers()
        feed.removeUser(userWillBeRemovedBeforeUpdate)
        feed.updateUser(usersToUpdate.find { it.login == willBeModifiedBeforeUpdateLogin }!!.withPollingCompleteUntil(expectedModifiedPollingCompleteUntil))
        feed.compareAndSetPollingCompleteUntilForUsers(usersToUpdate, expectedUpdatedPollingCompleteUntil)

        assertUsers(
            setOf(
                userWillBeUpdated.withPollingCompleteUntil(expectedUpdatedPollingCompleteUntil),
                userWillBeModifiedBeforeUpdate.withPollingCompleteUntil(expectedModifiedPollingCompleteUntil)
            ),
            feed.getUsers()
        )
    }

    @Test
    fun `start and stop work as expected`() {
        val feed = createInvoiceFeed()
        assertFalse(feed.isRunning())

        feed.start()
        assertTrue(feed.isRunning())

        feed.stop()
        assertFalse(feed.isRunning())
    }

    @Test
    fun `destroy saves users and stops feed`() {
        val userToAdd = createTechnicalUser("toAdd", now.minusDays(1))

        val feed = mockAutowiredFieldsAndRunPostConstruct(TestFeedWithSaveMethods())
        feed.init()
        assertTrue(feed.isRunning())
        feed.addUser(userToAdd)

        expectedSavedUsers = setOf(userToAdd)
        feed.destroy()
        assertFalse(feed.isRunning())
    }

    private fun createInvoiceFeed(pastFetchingPeriod: Int? = null): TestFeed {
        return mockAutowiredFieldsAndRunPostConstruct(TestFeed(pastFetchingPeriod))
    }

    private fun <F: InvoiceFeed> mockAutowiredFieldsAndRunPostConstruct(feed: F): F {
        setInvoiceFeedField(feed, "librarySettings", librarySettings)
        setInvoiceFeedField(feed, "currentTimeProvider", currentTimeProvider)
        val postConstructMethod = InvoiceFeed::class.java.getDeclaredMethod("postConstruct")
        Method::class.java.getDeclaredField("modifiers").apply {
            trySetAccessible()
            setInt(postConstructMethod, postConstructMethod.modifiers and Modifier.FINAL.inv())
        }
        postConstructMethod.trySetAccessible()
        postConstructMethod.invoke(feed)
        return feed
    }

    private fun setInvoiceFeedField(feed: InvoiceFeed, fieldName: String, value: Any?) {
        val field = InvoiceFeed::class.java.getDeclaredField(fieldName)
        field.trySetAccessible()
        field.set(feed, value)
    }

    private fun createAndInitInvoiceFeed(pastFetchingPeriod: Int? = null): TestFeed {
        return createInvoiceFeed(pastFetchingPeriod).apply { init() }
    }

    private fun assertUsers(expectedUsers: Set<TechnicalUser>, actualUsers: Set<TechnicalUser>) {
        assertSetsContainSameElements(expectedUsers, actualUsers, this::isSameTechnicalUser)
    }

    private fun isSameTechnicalUser(user: TechnicalUser, otherUser: TechnicalUser): Boolean {
        return user.login == otherUser.login
                && user.pollingCompleteUntil == otherUser.pollingCompleteUntil
                && user.pollingDirections == otherUser.pollingDirections
    }
}