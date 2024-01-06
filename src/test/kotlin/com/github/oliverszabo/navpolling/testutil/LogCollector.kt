package com.github.oliverszabo.navpolling.testutil

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory


class LogCollector private constructor(): ListAppender<ILoggingEvent>() {
    companion object {
        fun create(clazz: Class<*>): LogCollector {
            val logger: Logger = LoggerFactory.getLogger(clazz) as Logger
            val logCollector = LogCollector()
            logCollector.setContext(LoggerFactory.getILoggerFactory() as LoggerContext)
            logger.level = Level.DEBUG
            logger.addAppender(logCollector)
            logCollector.start()
            return logCollector
        }
    }

    fun reset() {
        list.clear()
    }

    fun contains(string: String?, level: Level): Boolean {
        return list.any(eventMatcher(string, level))
    }

    fun containsOnce(string: String?, level: Level): Boolean {
        return list.filter(eventMatcher(string, level)).size == 1
    }

    fun countOfLoggedEvents(level: Level): Int {
        return list.filter { event -> event.level == level }.size
    }

    private fun eventMatcher(string: String?, level: Level): (ILoggingEvent) -> Boolean {
        return { event -> event.toString().contains(string!!) && event.level == level }
    }

    /*fun countEventsForLogger(loggerName: String?): Int {
        return list.stream()
            .filter(Predicate { event: ILoggingEvent ->
                event.loggerName.contains(loggerName!!)
            })
            .count().toInt()
    }

    fun search(string: String?): List<ILoggingEvent> {
        return list.stream()
            .filter(Predicate { event: ILoggingEvent ->
                event.toString().contains(string!!)
            })
            .collect(Collectors.toList())
    }

    fun search(string: String?, level: Level): List<ILoggingEvent> {
        return list.stream()
            .filter(Predicate { event: ILoggingEvent ->
                (event.toString().contains(string!!)
                        && event.level == level)
            })
            .collect(Collectors.toList())
    }*/

    val size: Int
        get() = list.size
    val loggedEvents: List<ILoggingEvent?>
        get() = list
}