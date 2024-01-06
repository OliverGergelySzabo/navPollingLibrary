package com.github.oliverszabo.navpolling.eventpublishing

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.github.oliverszabo.navpolling.VoidInvoiceFeed
import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.annotation.OnInvoiceArrived
import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.util.createXmlMapper
import org.springframework.context.ApplicationContext
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.lang.reflect.Method

@Component
class EventPublisherFactory(
    private val invoiceFieldFactory: InvoiceFieldFactory,
    private val invoiceFeeds: List<InvoiceFeed>,
    private val applicationContext: ApplicationContext,
): SmartLifecycle {
    companion object {
        const val EVENT_PUBLISHER_CREATION_FAILED_ERROR_TEMPLATE = "The creation of event publishers failed caused by: %s"
    }

    private var isRunning: Boolean = false
    private lateinit var eventPublishersByInvoiceFeedClass: Map<Class<out InvoiceFeed>, List<EventPublisher>>

    @Synchronized
    override fun start() {
        this.eventPublishersByInvoiceFeedClass =  if(invoiceFeeds.isNotEmpty()) {
            val beanEventHandlerMethodPairsByInvoiceFeedClass = applicationContext.beanDefinitionNames
                .filter { applicationContext.isSingleton(it) }
                .map { applicationContext.getBean(it) }
                .flatMap { bean ->
                    bean.javaClass
                        .declaredMethods
                        .filter { it.isAnnotationPresent(OnInvoiceArrived::class.java) }
                        .map { Pair(bean, it) }
                }
                .groupBy { it.second
                    .getAnnotation(OnInvoiceArrived::class.java)
                    .invoiceFeed
                    .java
                }

            val eventPublishersByInvoiceFeedClass: MutableMap<Class<out InvoiceFeed>, MutableList<EventPublisher>> = invoiceFeeds
                .filter { beanEventHandlerMethodPairsByInvoiceFeedClass.contains(it.javaClass) }
                .associate { feed ->
                    val beanMethodPairs = beanEventHandlerMethodPairsByInvoiceFeedClass[feed.javaClass]!!
                    val mapper = createXmlMapper()
                    Pair(
                        feed.javaClass,
                        beanMethodPairs.map {
                            createEventPublisher(it.first, it.second, mapper)
                        }
                            .toMutableList()
                    )
                }
                .toMutableMap()

            //selecting the feed has with the lowest canonical class name (in order for the selection process to be deterministic)
            val defaultFeed = invoiceFeeds.minByOrNull { it.javaClass.canonicalName }!!::class.java
            if(!eventPublishersByInvoiceFeedClass.contains(defaultFeed)) {
                eventPublishersByInvoiceFeedClass[defaultFeed] = mutableListOf()
            }
            val beanMethodPairsForDefaultFeed = beanEventHandlerMethodPairsByInvoiceFeedClass[VoidInvoiceFeed::class.java] ?: emptyList()
            val mapper = createXmlMapper()
            eventPublishersByInvoiceFeedClass[defaultFeed]!!.addAll(
                beanMethodPairsForDefaultFeed.map { createEventPublisher(it.first, it.second, mapper) }
            )

            eventPublishersByInvoiceFeedClass
        } else {
            emptyMap()
        }
        isRunning = true
    }

    @Synchronized
    override fun stop() {
        isRunning = false
    }

    @Synchronized
    override fun isRunning(): Boolean {
        return isRunning
    }

    @Synchronized
    fun getEventPublishers(invoiceFeedClass: Class<out InvoiceFeed>): List<EventPublisher> {
        return eventPublishersByInvoiceFeedClass[invoiceFeedClass] ?: emptyList()
    }

    private fun createEventPublisher(eventHandlerObject: Any, eventHandlerMethod: Method, mapper: XmlMapper): EventPublisher {
        try {
            return EventPublisher(eventHandlerObject, eventHandlerMethod, invoiceFieldFactory, mapper)
        } catch (e: IllegalArgumentException) {
            throw NavPollingLibraryInitializationException(EVENT_PUBLISHER_CREATION_FAILED_ERROR_TEMPLATE.format(e.message), e)
        }
    }
}