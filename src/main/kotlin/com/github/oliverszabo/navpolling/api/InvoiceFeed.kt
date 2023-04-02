package com.github.oliverszabo.navpolling.api

abstract class InvoiceFeed(
    //todo: this has to come from config
    val pastFetchingPeriod: Int = 0
) {
    private var isRunning = false
    private val users: MutableSet<TechnicalUser> = mutableSetOf()

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
}