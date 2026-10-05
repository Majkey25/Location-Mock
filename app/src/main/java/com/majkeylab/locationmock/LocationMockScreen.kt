package com.majkeylab.locationmock

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class LocationPreset(
    val name: String,
    val coordinates: Coordinates,
)

private enum class InfoDialog { SETUP, PRIVACY, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationMockScreen(
    initialLatitude: String,
    initialLongitude: String,
    active: Boolean,
    starting: Boolean,
    stopping: Boolean,
    mockAppAllowed: Boolean,
    serviceError: String?,
    onStart: (Coordinates) -> Unit,
    onStop: () -> Unit,
    onAddressSearch: suspend (String) -> List<AddressSearchResult>,
    onOpenDeveloperOptions: () -> Unit,
) {
    var latitude by rememberSaveable { mutableStateOf(initialLatitude) }
    var longitude by rememberSaveable { mutableStateOf(initialLongitude) }
    var addressQuery by rememberSaveable { mutableStateOf("") }
    var addressResults by remember { mutableStateOf(emptyList<AddressSearchResult>()) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var addressSearching by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<InfoDialog?>(null) }
    val scope = rememberCoroutineScope()
    val noAddressResults = stringResource(R.string.address_no_results)
    val addressSearchFailed = stringResource(R.string.address_search_failed)
    val inputEnabled = !active && !starting && !stopping && !addressSearching

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        InfoDialog.entries.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            when (item) {
                                                InfoDialog.SETUP -> R.string.menu_setup
                                                InfoDialog.PRIVACY -> R.string.menu_privacy
                                                InfoDialog.ABOUT -> R.string.menu_about
                                            },
                                        ),
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    dialog = item
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                StatusSurface(
                    active = active,
                    starting = starting,
                    stopping = stopping,
                    mockAppAllowed = mockAppAllowed,
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.coordinates_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.coordinates_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = addressQuery,
                    onValueChange = {
                        addressQuery = it
                        addressError = null
                    },
                    label = { Text(stringResource(R.string.address_search_label)) },
                    singleLine = true,
                    enabled = inputEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        addressSearching = true
                        addressError = null
                        addressResults = emptyList()
                        scope.launch {
                            try {
                                addressResults = onAddressSearch(addressQuery)
                                if (addressResults.isEmpty()) addressError = noAddressResults
                            } catch (cause: CancellationException) {
                                throw cause
                            } catch (cause: Exception) {
                                addressError = cause.message ?: addressSearchFailed
                            } finally {
                                addressSearching = false
                            }
                        }
                    },
                    enabled = inputEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (addressSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        stringResource(
                            if (addressSearching) R.string.address_searching else R.string.address_search_action,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.address_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                addressError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                addressResults.forEach { result ->
                    TextButton(
                        onClick = {
                            latitude = result.coordinates.latitude.toString()
                            longitude = result.coordinates.longitude.toString()
                            addressResults = emptyList()
                            addressError = null
                            inputError = null
                        },
                        enabled = inputEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(result.label, fontWeight = FontWeight.Medium)
                            Text(
                                "${result.coordinates.latitude}, ${result.coordinates.longitude}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it; inputError = null },
                        label = { Text(stringResource(R.string.latitude)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = inputEnabled,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it; inputError = null },
                        label = { Text(stringResource(R.string.longitude)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = inputEnabled,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Presets(enabled = inputEnabled, onSelect = {
                    latitude = it.latitude.toString()
                    longitude = it.longitude.toString()
                    inputError = null
                })
                (inputError ?: serviceError)?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (active || starting) {
                            onStop()
                        } else {
                            runCatching { Coordinates.parse(latitude, longitude) }
                                .onSuccess(onStart)
                                .onFailure { inputError = it.message }
                        }
                    },
                    enabled = !stopping,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            when {
                                stopping -> R.string.stopping_mock
                                active || starting -> R.string.stop_mock
                                else -> R.string.start_mock
                            },
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        when {
                            stopping -> R.string.stopping_help
                            active -> R.string.active_help
                            starting -> R.string.starting_help
                            !mockAppAllowed -> R.string.inactive_help
                            else -> R.string.ready_help
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(24.dp))
                VpnFeatureSection()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.pricing_note),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    dialog?.let { selected ->
        InfoDialog(
            selected = selected,
            onDismiss = { dialog = null },
            onOpenDeveloperOptions = onOpenDeveloperOptions,
        )
    }
}

@Composable
private fun StatusSurface(
    active: Boolean,
    starting: Boolean,
    stopping: Boolean,
    mockAppAllowed: Boolean,
) {
    val statusColor = when {
        stopping -> MaterialTheme.colorScheme.primary
        active -> Color(0xFF00796B)
        starting -> MaterialTheme.colorScheme.primary
        !mockAppAllowed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when {
        stopping -> R.string.status_stopping
        active -> R.string.status_active
        starting -> R.string.status_starting
        !mockAppAllowed -> R.string.status_setup
        else -> R.string.status_stopped
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(Modifier.size(10.dp).background(statusColor, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(statusText), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Presets(enabled: Boolean, onSelect: (Coordinates) -> Unit) {
    val presets = listOf(
        LocationPreset(stringResource(R.string.preset_prague), Coordinates(50.0755, 14.4378)),
        LocationPreset(stringResource(R.string.preset_london), Coordinates(51.5074, -0.1278)),
        LocationPreset(stringResource(R.string.preset_new_york), Coordinates(40.7128, -74.0060)),
        LocationPreset(stringResource(R.string.preset_tokyo), Coordinates(35.6762, 139.6503)),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        presets.forEach { preset ->
            AssistChip(
                onClick = { onSelect(preset.coordinates) },
                label = { Text(preset.name) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun InfoDialog(
    selected: InfoDialog,
    onDismiss: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var linkError by remember(selected) { mutableStateOf(false) }
    val title = when (selected) {
        InfoDialog.SETUP -> R.string.setup_title
        InfoDialog.PRIVACY -> R.string.privacy_title
        InfoDialog.ABOUT -> R.string.about_title
    }
    val body = when (selected) {
        InfoDialog.SETUP -> R.string.setup_body
        InfoDialog.PRIVACY -> R.string.privacy_body
        InfoDialog.ABOUT -> R.string.about_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(body))
                if (selected != InfoDialog.SETUP) {
                    TextButton(onClick = {
                        val page = if (selected == InfoDialog.PRIVACY) "privacy.html" else "terms.html"
                        linkError = runCatching {
                            uriHandler.openUri("https://majkey25.github.io/Location-Mock/$page")
                        }.isFailure
                    }) {
                        Text(stringResource(
                            if (selected == InfoDialog.PRIVACY) R.string.full_privacy_policy
                            else R.string.terms_and_notices,
                        ))
                    }
                    if (linkError) {
                        Text(stringResource(R.string.policy_link_error), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (selected == InfoDialog.SETUP) {
                Button(onClick = { onDismiss(); onOpenDeveloperOptions() }) {
                    Text(stringResource(R.string.open_developer_options))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
        dismissButton = if (selected == InfoDialog.SETUP) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
        } else {
            null
        },
    )
}
