package com.github.oliverszabo.navpolling.api

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.util.CurrentTimeProvider
import com.github.oliverszabo.navpolling.util.ErrorMessages
import com.github.oliverszabo.navpolling.util.minusDays
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PostConstruct

abstract class InvoiceFeed(
    pastFetchingPeriod: Int? = null
) {
    private var pastFetchingPeriod: Int

    private var isRunning = AtomicBoolean(false)
    private val usersByLogin = mutableMapOf<String, TechnicalUser>()

    @Autowired
    private lateinit var librarySettings: LibrarySettings

    @Autowired
    private lateinit var currentTimeProvider: CurrentTimeProvider

    init {
        if(pastFetchingPeriod != null && pastFetchingPeriod < 0) {
            throw NavPollingLibraryInitializationException(ErrorMessages.paramMustBeGreaterThanOrEqualTo("pastFetchingPeriod", 0))
        }
        this.pastFetchingPeriod = pastFetchingPeriod ?: -1
    }

    @PostConstruct
    private fun postConstruct() {
        if(pastFetchingPeriod == -1) {
            pastFetchingPeriod = librarySettings.defaultPastFetchingPeriod
        }
    }

    open fun loadUsers(): Set<TechnicalUser> {
        return emptySet()
    }

    open fun saveUsers(users: Set<TechnicalUser>) {

    }

    @Synchronized
    fun getUsers(): Set<TechnicalUser> {
        return usersByLogin.values.toSet()
    }

    @Synchronized
    fun addUser(user: TechnicalUser) {
        usersByLogin[user.login] = if(user.pollingCompleteUntil == null) {
            user.withPollingCompleteUntil(currentTimeProvider.currentSecond().minusDays(pastFetchingPeriod))
        } else {
            user
        }
    }

    @Synchronized
    fun getUser(userLogin: String): TechnicalUser? {
        return usersByLogin[userLogin]
    }

    @Synchronized
    fun updateUser(updatedUser: TechnicalUser) {
        if(usersByLogin.contains(updatedUser.login)) {
            usersByLogin[updatedUser.login] = updatedUser
        }
    }

    @Synchronized
    fun removeUser(userLogin: String) {
        usersByLogin.remove(userLogin)
    }

    @Synchronized
    fun removeUser(user: TechnicalUser) {
        usersByLogin.remove(user.login)
    }

    @Synchronized
    fun clearUsers() {
        usersByLogin.clear()
    }

    fun isRunning(): Boolean {
        return  isRunning.get()
    }

    fun start() {
         isRunning.set(true)
    }

    fun stop() {
         isRunning.set(false)
    }

    fun getPastFetchingPeriod(): Int {
        return pastFetchingPeriod
    }

    @Synchronized
    internal fun init() {
        val now = currentTimeProvider.currentSecond()
        usersByLogin.putAll(
            loadUsers()
            .map { if(it.pollingCompleteUntil == null) it.withPollingCompleteUntil(now) else it }
            .map { Pair(it.login, it) }
        )
        start()
    }

    @Synchronized
    internal fun destroy() {
        saveUsers()
        stop()
    }

    @Synchronized
    internal fun compareAndSetPollingCompleteUntilForUsers(users: Set<TechnicalUser>, newValue: Instant) {
        users.forEach { user ->
            val existingUser = usersByLogin[user.login]
            if(existingUser != null && existingUser.pollingCompleteUntil == user.pollingCompleteUntil) {
                usersByLogin[user.login] = existingUser.withPollingCompleteUntil(newValue)
            }
        }
    }

    @Synchronized
    internal fun saveUsers() {
        saveUsers(getUsers())
    }
}