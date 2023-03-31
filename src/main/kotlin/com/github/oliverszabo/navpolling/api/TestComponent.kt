package com.github.oliverszabo.navpolling.api

import org.springframework.stereotype.Component

@Component
class TestComponent {
    fun add(a: Int, b: Int): Int {
        return a + b
    }
}