package com.github.oliverszabo.navpolling.util

import com.github.oliverszabo.navpolling.api.InvoiceDirection
import com.github.oliverszabo.navpolling.api.TechnicalUser
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

fun <E, A> assertListsContainSameElements(expectedList: List<E>, actualList: List<A>, comparator: (E, A) -> Boolean) {
    assertEquals(expectedList.size, actualList.size)
    val mutableActualList = actualList.toMutableList()
    expectedList.forEach { expectedElement ->
        val index = mutableActualList.indexOfFirst { comparator(expectedElement, it) }
        assertNotEquals(-1, index, "Element '$expectedElement' was not found in actual list")
        mutableActualList.removeAt(index)
    }
}

fun <E, A> assertSetsContainSameElements(expectedSet: Set<E>, actualSet: Set<A>, comparator: (E, A) -> Boolean) {
    assertListsContainSameElements(expectedSet.toList(), actualSet.toList(), comparator)
}

inline fun <reified T: Throwable>assertThrownException(expectedMessage: String, executable: () -> Unit) {
    val exception: T = assertThrows(executable)
    assertEquals(expectedMessage, exception.message)
}

fun assertEmpty(collection: Collection<*>) {
    assertTrue(collection.isEmpty())
}

fun createTechnicalUser(
    login: String,
    pollingCompleteUntil: Instant? = null,
): TechnicalUser {
    return TechnicalUser(login, "p", "t", "s", InvoiceDirection.values().toSet(), pollingCompleteUntil)
}
