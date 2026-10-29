package id.tellolabs.launcher.ui.settings.backup

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import id.tellolabs.launcher.R
import id.tellolabs.launcher.data.backup.SettingsBackup

@Composable
internal fun SettingsImportDialog(backup: SettingsBackup, busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(stringResource(R.string.backup_replace_title)) },
        text = {
            Text(stringResource(R.string.backup_replace_message,
                backup.settings.shelves.size, backup.settings.hiddenPackages.size,
                backup.settings.protectedPackages.size),
                modifier = Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(stringResource(if (busy) R.string.backup_working else R.string.backup_replace))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}
