package com.majkeylab.locationmock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoordinatesTest {
    @Test
    fun parsesDecimalCoordinates() {
        assertEquals(Coordinates(50.0755, 14.4378), Coordinates.parse(" 50.0755 ", "14.4378"))
    }

    @Test
    fun parsesDecimalComma() {
        assertEquals(Coordinates(40.7128, -74.006), Coordinates.parse("40,7128", "-74,006"))
    }

    @Test
    fun acceptsExactWorldBounds() {
        assertEquals(Coordinates(-90.0, 180.0), Coordinates.parse("-90", "180"))
    }

    @Test
    fun rejectsInvalidNumber() {
        val error = assertFailsWith<IllegalArgumentException> {
            Coordinates.parse("north", "14.4")
        }

        assertEquals("Enter a valid latitude.", error.message)
    }

    @Test
    fun rejectsOutOfRangeValues() {
        assertEquals(
            "Latitude must be between -90 and 90.",
            assertFailsWith<IllegalArgumentException> { Coordinates.parse("90.1", "0") }.message,
        )
        assertEquals(
            "Longitude must be between -180 and 180.",
            assertFailsWith<IllegalArgumentException> { Coordinates.parse("0", "-180.1") }.message,
        )
    }
}
