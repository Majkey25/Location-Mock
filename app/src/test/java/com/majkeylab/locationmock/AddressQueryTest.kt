package com.majkeylab.locationmock

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AddressQueryTest {
    @Test
    fun trimsValidQuery() {
        assertEquals("Gorilla Hall Osaka", AddressQuery.parse("  Gorilla Hall Osaka  ").value)
    }

    @Test
    fun rejectsTooShortQuery() {
        assertEquals(
            "Enter at least 3 characters.",
            assertFailsWith<IllegalArgumentException> { AddressQuery.parse("ab") }.message,
        )
    }

    @Test
    fun rejectsTooLongQuery() {
        assertEquals(
            "Address must be 200 characters or fewer.",
            assertFailsWith<IllegalArgumentException> {
                AddressQuery.parse("a".repeat(201))
            }.message,
        )
    }
}
