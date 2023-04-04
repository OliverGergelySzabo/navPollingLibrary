package com.github.oliverszabo.navpolling.api

import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.config.LibrarySettings
import com.github.oliverszabo.navpolling.util.ErrorMessages
import org.springframework.beans.factory.annotation.Autowired
import javax.annotation.PostConstruct


abstract class InvoiceFeed(
    pastFetchingPeriod: Int? = null
) {
    private var pastFetchingPeriod: Int = pastFetchingPeriod ?: -1
    private var isRunning = false
    private val users: MutableSet<TechnicalUser> = mutableSetOf()

    @Autowired
    private lateinit var librarySettings: LibrarySettings

    init {
        if(pastFetchingPeriod != null && pastFetchingPeriod < 0) {
            throw NavPollingLibraryInitializationException(ErrorMessages.paramMustBeGreaterThanOrEqualTo("pastFetchingPeriod", 0))
        }
        this.pastFetchingPeriod = pastFetchingPeriod ?: -1
    }

    @PostConstruct
    fun init() {
        if(pastFetchingPeriod == -1) {
            pastFetchingPeriod = librarySettings.defaultPastFetchingPeriod
        }
    }

    abstract fun initialUsers(): Set<TechnicalUser>

    open fun loadState(): Any {
        return Any()
    }

    open fun saveState(feedState: Any) {

    }

    fun loadUsers() {
        users.addAll(initialUsers())
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
}