package com.github.oliverszabo.navpolling.integration

object IntegrationTestHelper {
    private val completionMonitor = Object()
    private const val MAX_WAIT_TIME_MILIS = 10 * 1000L

    fun waitForTestCompletion() {
        synchronized(completionMonitor) {
            val currentTime = System.currentTimeMillis()
            completionMonitor.wait(MAX_WAIT_TIME_MILIS)
            if(System.currentTimeMillis() - currentTime > MAX_WAIT_TIME_MILIS) {
                throw Exception("Test timed out")
            }
        }
    }

    fun signalTestCompletion() {
        synchronized(completionMonitor) {
            completionMonitor.notifyAll()
        }
    }
}