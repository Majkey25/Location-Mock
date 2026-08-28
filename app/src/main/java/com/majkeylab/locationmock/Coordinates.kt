package com.majkeylab.locationmock

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite()) { "Enter a valid latitude." }
        require(longitude.isFinite()) { "Enter a valid longitude." }
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
    }

    companion object {
        fun of(latitude: Double, longitude: Double): Coordinates = Coordinates(latitude, longitude)

        fun parse(latitude: String, longitude: String): Coordinates {
            val parsedLatitude = latitude.toCoordinateOrNull()
                ?: throw IllegalArgumentException("Enter a valid latitude.")
            val parsedLongitude = longitude.toCoordinateOrNull()
                ?: throw IllegalArgumentException("Enter a valid longitude.")
            return Coordinates(parsedLatitude, parsedLongitude)
        }

        private fun String.toCoordinateOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
    }
}
