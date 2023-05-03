package com.github.oliverszabo.navpolling.api

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.util.ErrorMessages
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PostConstruct

abstract class InvoiceFeed(
    pastFetchingPeriod: Int? = null
) {
    private var pastFetchingPeriod: Int

    private var isRunning = AtomicBoolean(false)
    private var pollingCompleteUntil: Instant = Instant.now()
    private val users: MutableSet<TechnicalUser> = mutableSetOf()
    private val newlyAddedTechnicalUserLogins: MutableSet<String> = mutableSetOf()

    @Autowired
    private lateinit var librarySettings: LibrarySettings

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

    open fun loadState(): State? {
        return null
    }

    open fun saveState(feedState: State) {

    }

    open fun loadUsers(): Set<TechnicalUser> {
        return emptySet()
    }

    open fun saveUsers(users: Set<TechnicalUser>) {

    }

    @Synchronized
    fun getUsers(): Set<TechnicalUser> {
        return users
    }

    @Synchronized
    fun addUser(user: TechnicalUser) {
        users.add(user)
        newlyAddedTechnicalUserLogins.add(user.login)
    }

    @Synchronized
    fun removeUser(userLogin: String) {
        users.removeIf { it.login == userLogin }
        newlyAddedTechnicalUserLogins.remove(userLogin)
    }

    fun removeUser(user: TechnicalUser) {
        removeUser(user.login)
    }

    @Synchronized
    fun clearUsers() {
        users.clear()
        newlyAddedTechnicalUserLogins.clear()
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
        users.addAll(loadUsers())
        val state = loadState()
        if(state != null) {
            pollingCompleteUntil = state.pollingCompleteUntil
            newlyAddedTechnicalUserLogins.addAll(state.newlyAddedTechnicalUserLogins)
        }
        start()
    }

    @Synchronized
    internal fun destroy() {
        saveState(State(pollingCompleteUntil, newlyAddedTechnicalUserLogins))
        saveUsers(users)
        stop()
    }

    @Synchronized
    internal fun getUsers(isPastFetchingRequired: Boolean): Set<TechnicalUser> {
        return users.filter { newlyAddedTechnicalUserLogins.contains(it.login) == isPastFetchingRequired }.toSet()
    }

    @Synchronized
    internal fun onPastFetchingCompleted(users: Set<TechnicalUser>) {
        newlyAddedTechnicalUserLogins.removeAll(users.map { it.login }.toSet())
    }

    @Synchronized
    internal fun getPollingCompleteUntil(): Instant {
        return pollingCompleteUntil
    }

    @Synchronized
    internal fun setPollingCompleteUntil(newValue: Instant) {
        pollingCompleteUntil = newValue
    }

    //todo: make this class serializable?
    data class State internal constructor(
        val pollingCompleteUntil: Instant,
        val newlyAddedTechnicalUserLogins: Set<String>
    ) {
        companion object {
            @JvmStatic
            fun fromJson(json: String): State {
                TODO()
            }
        }

        fun toJson(): String {
            TODO()
        }
    }
}