package com.majkeylab.locationmock

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@JvmInline
value class AddressQuery private constructor(val value: String) {
    companion object {
        fun parse(raw: String): AddressQuery {
            val value = raw.trim()
            require(value.length >= MIN_QUERY_LENGTH) { "Enter at least 3 characters." }
            require(value.length <= MAX_QUERY_LENGTH) { "Address must be 200 characters or fewer." }
            return AddressQuery(value)
        }

        private const val MIN_QUERY_LENGTH = 3
        private const val MAX_QUERY_LENGTH = 200
    }
}

data class AddressSearchResult(
    val label: String,
    val coordinates: Coordinates,
)

class AddressSearchRepository(context: Context) {
    private val geocoder = Geocoder(context.applicationContext, Locale.getDefault())

    suspend fun search(rawQuery: String): List<AddressSearchResult> {
        val query = AddressQuery.parse(rawQuery)
        check(Geocoder.isPresent()) { "Address search is unavailable on this device." }
        val addresses = try {
            withTimeout(SEARCH_TIMEOUT_MILLIS) { queryAddresses(query.value) }
        } catch (cause: TimeoutCancellationException) {
            throw IOException("Address search timed out.", cause)
        }
        return addresses.mapNotNull { it.toSearchResult() }
            .distinctBy { Triple(it.label, it.coordinates.latitude, it.coordinates.longitude) }
            .take(MAX_RESULTS)
    }

    private suspend fun queryAddresses(query: String): List<Address> =
        if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(
                    query,
                    MAX_RESULTS,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<Address>) {
                            if (continuation.isActive) continuation.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IOException("Address search failed."),
                                )
                            }
                        }
                    },
                )
            }
        } else {
            @Suppress("DEPRECATION")
            withContext(Dispatchers.IO) {
                geocoder.getFromLocationName(query, MAX_RESULTS).orEmpty()
            }
        }

    private fun Address.toSearchResult(): AddressSearchResult? {
        val coordinates = runCatching { Coordinates.of(latitude, longitude) }.getOrNull() ?: return null
        val parts = listOfNotNull(featureName, locality, adminArea, countryName)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        val label = getAddressLine(0)?.trim()?.takeIf(String::isNotEmpty)
            ?: parts.joinToString().takeIf(String::isNotEmpty)
            ?: "${coordinates.latitude}, ${coordinates.longitude}"
        return AddressSearchResult(label, coordinates)
    }

    private companion object {
        const val MAX_RESULTS = 5
        const val SEARCH_TIMEOUT_MILLIS = 10_000L
    }
}
