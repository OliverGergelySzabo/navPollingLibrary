package com.github.oliverszabo.navpolling.util

import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*

fun <E, A> assertListsContainSameElements(expectedList: List<E>, actualList: List<A>, comparator: (E, A) -> Boolean) {
    assertEquals(expectedList.size, actualList.size)
    val mutableActualList = actualList.toMutableList()
    expectedList.forEach { expectedElement ->
        val index = mutableActualList.indexOfFirst { comparator(expectedElement, it) }
        assertNotEquals(-1, index, "Element '$expectedElement' was not found in actual list")
        mutableActualList.removeAt(index)
    }
}

inline fun <reified T: Throwable>assertThrownException(expectedMessage: String, executable: () -> Unit) {
    val exception: T = assertThrows(executable)
    assertEquals(expectedMessage, exception.message)
}

fun assertEmpty(collection: Collection<*>) {
    assertTrue(collection.isEmpty())
}