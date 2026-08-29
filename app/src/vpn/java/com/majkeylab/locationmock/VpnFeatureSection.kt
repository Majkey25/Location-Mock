package com.majkeylab.locationmock

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VpnFeatureSection() {
    val context = LocalContext.current
    val controller = remember(context.applicationContext) {
        WireGuardVpnController(context.applicationContext)
    }
    val preferences = remember {
        context.getSharedPreferences(VPN_UI_PREFERENCES, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(controller.state) }
    var hasConfiguration by remember { mutableStateOf(controller.hasConfiguration) }
    var controllerError by remember { mutableStateOf(controller.error) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var disclosureOpen by remember { mutableStateOf(false) }
    var disclosureAccepted by rememberSaveable {
        mutableStateOf(preferences.getBoolean(KEY_DISCLOSURE_ACCEPTED, false))
    }
    val permissionDenied = stringResource(R.string.vpn_permission_denied)
    val importFailed = stringResource(R.string.vpn_import_failed)

    fun perform(operation: suspend () -> Result<Unit>) {
        actionError = null
        scope.launch {
            actionError = operation().exceptionOrNull()?.message
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            perform(controller::connect)
        } else {
            actionError = permissionDenied
        }
    }

    fun requestConnection() {
        actionError = null
        val permissionIntent = runCatching(controller::permissionIntent).getOrElse {
            actionError = it.message ?: permissionDenied
            return
        }
        if (permissionIntent == null) {
            perform(controller::connect)
        } else {
            permissionLauncher.launch(permissionIntent)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            actionError = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@withContext Result.failure(IllegalStateException(importFailed))
                    input.use { controller.importConfiguration(it) }
                }
                actionError = result.exceptionOrNull()?.message
            }
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            state = controller.state
            hasConfiguration = controller.hasConfiguration
            controllerError = controller.error
            delay(STATE_REFRESH_MILLIS)
        }
    }

    val busy = state == VpnUiState.CONNECTING || state == VpnUiState.DISCONNECTING
    val connected = state == VpnUiState.CONNECTED
    val stateText = stringResource(
        when (state) {
            VpnUiState.NO_CONFIG -> R.string.vpn_no_config
            VpnUiState.DISCONNECTED -> R.string.vpn_disconnected
            VpnUiState.CONNECTING -> R.string.vpn_connecting
            VpnUiState.CONNECTED -> R.string.vpn_connected
            VpnUiState.DISCONNECTING -> R.string.vpn_disconnecting
            VpnUiState.ERROR -> R.string.vpn_error
        },
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.vpn_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.vpn_body), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            text = stateText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        (actionError ?: controllerError)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = {
                importLauncher.launch(
                    arrayOf("text/plain", "application/octet-stream", "application/x-wireguard-profile"),
                )
            },
            enabled = !busy && !connected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (hasConfiguration) R.string.vpn_replace else R.string.vpn_import))
        }
        if (hasConfiguration) {
            TextButton(
                onClick = { perform(controller::removeConfiguration) },
                enabled = !busy && !connected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.vpn_remove))
            }
        }
        Button(
            onClick = {
                if (connected) {
                    perform(controller::disconnect)
                } else if (!disclosureAccepted) {
                    disclosureOpen = true
                } else {
                    requestConnection()
                }
            },
            enabled = hasConfiguration && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (connected) R.string.vpn_disconnect else R.string.vpn_connect))
        }
        Spacer(Modifier.height(20.dp))
    }

    if (disclosureOpen) {
        AlertDialog(
            onDismissRequest = { disclosureOpen = false },
            title = { Text(stringResource(R.string.vpn_disclosure_title)) },
            text = { Text(stringResource(R.string.vpn_disclosure_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        disclosureOpen = false
                        disclosureAccepted = true
                        preferences.edit { putBoolean(KEY_DISCLOSURE_ACCEPTED, true) }
                        requestConnection()
                    },
                ) {
                    Text(stringResource(R.string.vpn_disclosure_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { disclosureOpen = false }) {
                    Text(stringResource(R.string.vpn_disclosure_cancel))
                }
            },
        )
    }
}

private const val VPN_UI_PREFERENCES = "vpn_ui"
private const val KEY_DISCLOSURE_ACCEPTED = "disclosure_accepted"
private const val STATE_REFRESH_MILLIS = 500L
