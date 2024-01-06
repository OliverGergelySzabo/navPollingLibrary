package com.github.oliverszabo.navpolling.util

import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class CurrentTimeProvider {
    fun currentSecond(): Instant {
        //TODO: introduce current instant provider for customizability
        return Instant.now().truncatedTo(ChronoUnit.SECONDS)
    }
}