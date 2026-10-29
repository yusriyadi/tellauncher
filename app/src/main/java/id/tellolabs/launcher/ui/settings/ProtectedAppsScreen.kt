package id.tellolabs.launcher.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tellolabs.launcher.R
import id.tellolabs.launcher.data.preferences.LauncherPreferences
import id.tellolabs.launcher.model.LauncherApp
import id.tellolabs.launcher.ui.components.AppIcon
import id.tellolabs.launcher.ui.components.EmptyMessage
import id.tellolabs.launcher.ui.components.PageHeader
import id.tellolabs.launcher.ui.components.SearchField
import id.tellolabs.launcher.ui.components.ToggleSetting

@Composable
internal fun ProtectedAppsScreen(
    model: SettingsViewModel,
    preferences: LauncherPreferences?,
    apps: List<LauncherApp>?,
    preferenceError: Boolean,
    appError: Boolean,
    protectedQuery: String,
    onQueryChange: (String) -> Unit,
    authenticationSettingsUnlocked: Boolean,
    onLockAuthenticationSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
    ) {
        PageHeader(R.string.protected_apps) {
            keyboard?.hide()
            onLockAuthenticationSettings()
            onBack()
        }
        Text(stringResource(R.string.protected_apps_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !authenticationSettingsUnlocked -> EmptyMessage(R.string.authentication_required)
                preferenceError -> EmptyMessage(R.string.preferences_error)
                appError -> EmptyMessage(R.string.load_error)
                apps == null || preferences == null -> EmptyMessage(R.string.loading)
                else -> {
                    val packages = remember(apps, protectedQuery) {
                        apps.orEmpty().distinctBy { it.packageName }
                            .filter { it.label.contains(protectedQuery.trim(), ignoreCase = true) }
                    }
                    if (packages.isEmpty()) {
                        EmptyMessage(if (protectedQuery.isBlank()) R.string.no_apps else R.string.no_results)
                    } else LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        items(packages, key = { it.packageName }) { app ->
                            ToggleSetting(
                                title = app.label,
                                checked = app.packageName in preferences!!.protectedPackages,
                                onChange = {
                                    if (authenticationSettingsUnlocked) model.setProtected(app.packageName, it)
                                },
                                leadingIcon = { AppIcon(app, Modifier.size(28.dp)) },
                            )
                        }
                    }
                }
            }
        }
        SearchField(protectedQuery, { onQueryChange(it) }, enabled = authenticationSettingsUnlocked)
    }
}
