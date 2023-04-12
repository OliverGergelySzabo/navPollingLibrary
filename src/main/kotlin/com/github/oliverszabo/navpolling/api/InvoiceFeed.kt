package com.github.oliverszabo.navpolling.api

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.util.ErrorMessages
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import javax.annotation.PostConstruct


abstract class InvoiceFeed(
    pastFetchingPeriod: Int? = null
) {
    private var pastFetchingPeriod: Int = pastFetchingPeriod ?: -1
    private var isRunning = false
    private val users: MutableSet<TechnicalUser> = mutableSetOf()
    internal var state: State = State.default()

    @Autowired
    private lateinit var librarySettings: LibrarySettings

    init {
        if(pastFetchingPeriod != null && pastFetchingPeriod < 0) {
            throw NavPollingLibraryInitializationException(ErrorMessages.paramMustBeGreaterThanOrEqualTo("pastFetchingPeriod", 0))
        }
        this.pastFetchingPeriod = pastFetchingPeriod ?: -1
    }

    @PostConstruct
    fun postConstruct() {
        if(pastFetchingPeriod == -1) {
            pastFetchingPeriod = librarySettings.defaultPastFetchingPeriod
        }
    }

    abstract fun initialUsers(): Set<TechnicalUser>

    open fun loadState(): State {
        return state
    }

    open fun saveState(feedState: State) {

    }

    internal fun init() {
        users.addAll(initialUsers())
        state = loadState()
        start()
    }

    internal fun destroy() {
        saveState(state)
        stop()
    }

    fun addUser(user: TechnicalUser) {
        TODO()
    }

    fun removeUser(userTaxNumber: String) {
        TODO()
    }

    fun clearUsers() {
        TODO()
    }

    fun isRunning(): Boolean {
        return isRunning
    }

    fun start() {
        isRunning = true
    }

    fun stop() {
        isRunning = false
    }

    fun getPastFetchingPeriod(): Int {
        return pastFetchingPeriod
    }

    fun getUsers(): Set<TechnicalUser> {
        return users
    }

    class State(val pollingCompleteUntil: Instant) {
        companion object {
            fun default(): State {
                return State(Instant.now())
            }
        }
    }
}