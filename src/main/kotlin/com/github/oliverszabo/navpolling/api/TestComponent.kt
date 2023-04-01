package com.github.oliverszabo.navpolling.api

import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
class TestComponent(
    private val interfaces: List<TestInterface>
): SmartLifecycle {
    private var isRunning: Boolean = false

    override fun start() {
        println("Starting background process: ${interfaces.size} objects detected")
        interfaces.forEach {
            println(it.sayHello())
        }
        isRunning = true
    }

    override fun stop() {
        println("Stopping background process")
        isRunning = false
    }

    override fun isRunning(): Boolean {
        return isRunning
    }
}