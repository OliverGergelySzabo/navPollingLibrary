package com.github.oliverszabo.navpolling.eventpublishing

import com.github.oliverszabo.navpolling.api.InvoiceFeed
import com.github.oliverszabo.navpolling.api.annotation.OnInvoiceArrived
import com.github.oliverszabo.navpolling.api.exception.NavPollingLibraryInitializationException
import com.github.oliverszabo.navpolling.model.InvoiceDigest
import com.github.oliverszabo.navpolling.util.assertEmpty
import com.github.oliverszabo.navpolling.util.assertListsContainSameElements
import com.github.oliverszabo.navpolling.util.assertThrownException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import java.lang.reflect.Method
import kotlin.reflect.jvm.javaMethod

class EventPublisherFactoryTest {
    class ClassWithInvalidHandlerMethod {
        @OnInvoiceArrived
        fun m() {}
    }

    class ClassWithDefaultHandlerMethod {
        @OnInvoiceArrived
        fun m(i: InvoiceDigest) {}

        fun notAnnotatedHandlerMethod(i: InvoiceDigest) {}

        fun notAnnotatedInvalidHandlerMethod() {}
    }

    class ClassWithFeedHandlerMethod {
        @OnInvoiceArrived(invoiceFeed = Feed::class)
        fun m(i: InvoiceDigest) {}

        fun notAnnotatedHandlerMethod(i: InvoiceDigest) {}

        fun notAnnotatedInvalidHandlerMethod() {}
    }

    class ClassWithOtherFeedHandlerMethod {
        @OnInvoiceArrived(invoiceFeed = OtherFeed::class)
        fun m(i: InvoiceDigest) {}

        fun notAnnotatedHandlerMethod(i: InvoiceDigest) {}

        fun notAnnotatedInvalidHandlerMethod() {}
    }

    class Feed: InvoiceFeed(1)
    class OtherFeed: InvoiceFeed(1)

    private var beanIsSingletonPairs: List<Pair<Any, Boolean>> = emptyList()
    private val applicationContext = mockk<ApplicationContext>(relaxed = true)

    private val invoiceFieldFactory = InvoiceFieldFactory()

    @BeforeEach
    fun beforeEach() {
        every { applicationContext.beanDefinitionNames } answers  {
            beanIsSingletonPairs.map { it.first.javaClass.simpleName }.toTypedArray()
        }

        every { applicationContext.isSingleton(any()) } answers {
            val name = firstArg<String>()
            (beanIsSingletonPairs.find { it.first.javaClass.simpleName == name } ?: throw NoSuchBeanDefinitionException(name)).second
        }

        every { applicationContext.getBean(any()) } answers {
            val name = firstArg<String>()
            (beanIsSingletonPairs.find { it.first.javaClass.simpleName == name } ?: throw NoSuchBeanDefinitionException(name)).first
        }
    }

    @Test
    fun startWrapsEventPublisherConstructorExceptionsIntoNavPollingLibraryInitializationExceptions() {
        beanIsSingletonPairs = listOf(Pair(ClassWithInvalidHandlerMethod(), true))

        assertThrownException<NavPollingLibraryInitializationException>(
            EventPublisherFactory.EVENT_PUBLISHER_CREATION_FAILED_ERROR_TEMPLATE
                .format(EventPublisher.EVENT_HANDLER_MUST_HAVE_AT_LEAST_ONE_ARGUMENT_ERROR_MESSAGE)
        ) {
            createAndStartEventPublisherFactory(listOf(Feed()))
        }
    }

    @Test
    fun handlersWithoutSpecifiedInvoiceFeedAreAssignedToDefaultFeed() {
        val handlerObject = ClassWithDefaultHandlerMethod()
        beanIsSingletonPairs = listOf(Pair(handlerObject, true))

        val factory = createAndStartEventPublisherFactory(listOf(Feed(), OtherFeed()))

        assertEventPublishers(
            listOf(Pair(handlerObject, ClassWithDefaultHandlerMethod::m.javaMethod)),
            factory.getEventPublishers(Feed::class.java))
        assertEmpty(factory.getEventPublishers(OtherFeed::class.java))
    }

    @Test
    fun eventPublishersAreOnlyCreatedForAutowiredFeeds() {
        val classWithDefaultHandlerMethod = ClassWithDefaultHandlerMethod()
        val classWithFeedHandlerMethod = ClassWithFeedHandlerMethod()
        val classWithOtherFeedHandlerMethod = ClassWithOtherFeedHandlerMethod()
        beanIsSingletonPairs = listOf(
            Pair(classWithDefaultHandlerMethod, true),
            Pair(classWithFeedHandlerMethod, true),
            Pair(classWithOtherFeedHandlerMethod, true)
        )

        val factory = createAndStartEventPublisherFactory(listOf(OtherFeed()))

        assertEmpty(factory.getEventPublishers(Feed::class.java))
        assertEventPublishers(
            listOf(
                Pair(classWithOtherFeedHandlerMethod, ClassWithOtherFeedHandlerMethod::m.javaMethod),
                Pair(classWithDefaultHandlerMethod, ClassWithDefaultHandlerMethod::m.javaMethod)
            ),
            factory.getEventPublishers(OtherFeed::class.java)
        )
    }

    @Test
    fun eventPublishersAreOnlyCreatedForSingletonHandlerClasses() {
        val classWithDefaultHandlerMethod = ClassWithDefaultHandlerMethod()
        val classWithFeedHandlerMethod = ClassWithFeedHandlerMethod()
        beanIsSingletonPairs = listOf(
            Pair(classWithDefaultHandlerMethod, true),
            Pair(classWithFeedHandlerMethod, false)
        )

        val factory = createAndStartEventPublisherFactory(listOf(Feed()))

        assertEventPublishers(
            listOf(Pair(classWithDefaultHandlerMethod, ClassWithDefaultHandlerMethod::m.javaMethod)),
            factory.getEventPublishers(Feed::class.java)
        )
    }

    @Test
    fun eventPublisherAreOnlyCreatedForHandlerClassesThatAreSpringBeans() {
        val classWithDefaultHandlerMethod = ClassWithDefaultHandlerMethod()
        val classWithFeedHandlerMethod = ClassWithFeedHandlerMethod()
        beanIsSingletonPairs = listOf(Pair(classWithDefaultHandlerMethod, true))

        val factory = createAndStartEventPublisherFactory(listOf(Feed()))

        assertEventPublishers(
            listOf(Pair(classWithDefaultHandlerMethod, ClassWithDefaultHandlerMethod::m.javaMethod)),
            factory.getEventPublishers(Feed::class.java)
        )
    }

    @Test
    fun eventPublishersAreCorrectlyAssignedToTheirHandlers() {
        val classWithDefaultHandlerMethod = ClassWithDefaultHandlerMethod()
        val classWithFeedHandlerMethod = ClassWithFeedHandlerMethod()
        val classWithOtherFeedHandlerMethod = ClassWithOtherFeedHandlerMethod()
        beanIsSingletonPairs = listOf(
            Pair(classWithDefaultHandlerMethod, true),
            Pair(classWithFeedHandlerMethod, true),
            Pair(classWithOtherFeedHandlerMethod, true)
        )

        val factory = createAndStartEventPublisherFactory(listOf(Feed(), OtherFeed()))

        assertEventPublishers(
            listOf(
                Pair(classWithDefaultHandlerMethod, ClassWithDefaultHandlerMethod::m.javaMethod),
                Pair(classWithFeedHandlerMethod, ClassWithFeedHandlerMethod::m.javaMethod)
            ),
            factory.getEventPublishers(Feed::class.java)
        )
        assertEventPublishers(
            listOf(Pair(classWithOtherFeedHandlerMethod, ClassWithOtherFeedHandlerMethod::m.javaMethod)),
            factory.getEventPublishers(OtherFeed::class.java)
        )
    }

    @Test
    fun `lifecycle methods are working correctly`() {
        val factory = createEventPublisherFactory(emptyList())
        assertFalse(factory.isRunning)
        factory.start()
        assertTrue(factory.isRunning)
        factory.stop()
        assertFalse(factory.isRunning)
    }

    private fun createEventPublisherFactory(feeds: List<InvoiceFeed>): EventPublisherFactory {
        return EventPublisherFactory(invoiceFieldFactory, feeds, applicationContext)
    }

    private fun createAndStartEventPublisherFactory(feeds: List<InvoiceFeed>): EventPublisherFactory {
        return createEventPublisherFactory(feeds).apply { start() }
    }

    private fun assertEventPublishers(expectedHandlerObjectMethodPairs: List<Pair<Any, Method?>>, eventPublishers: List<EventPublisher>) {
        assertListsContainSameElements(
            expectedHandlerObjectMethodPairs,
            eventPublishers
        ) { (expectedHandlerObject, expectedMethod), eventPublisher ->
            expectedHandlerObject == eventPublisher.eventHandlerObject && expectedMethod == eventPublisher.eventHandlerMethod
        }
    }
}