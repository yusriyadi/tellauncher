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
internal fun HiddenAppsScreen(
    model: SettingsViewModel,
    preferences: LauncherPreferences?,
    apps: List<LauncherApp>?,
    preferenceError: Boolean,
    appError: Boolean,
    hiddenQuery: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
    ) {
        PageHeader(R.string.hidden_apps) { keyboard?.hide(); onBack() }
        Text(
            stringResource(R.string.hidden_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                preferenceError -> EmptyMessage(R.string.preferences_error)
                appError -> EmptyMessage(R.string.load_error)
                apps == null || preferences == null -> EmptyMessage(R.string.loading)
                else -> {
                    val packages = remember(apps, hiddenQuery) {
                        apps.orEmpty().distinctBy { it.packageName }
                            .filter { it.label.contains(hiddenQuery.trim(), ignoreCase = true) }
                    }
                    if (packages.isEmpty()) {
                        EmptyMessage(if (hiddenQuery.isBlank()) R.string.no_apps else R.string.no_results)
                    } else LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        items(packages, key = { it.packageName }) { app ->
                            ToggleSetting(
                                title = app.label,
                                checked = app.packageName in preferences!!.hiddenPackages,
                                onChange = { model.setHidden(app.packageName, it) },
                                leadingIcon = { AppIcon(app, Modifier.size(28.dp)) },
                            )
                        }
                    }
                }
            }
        }
        SearchField(hiddenQuery, { onQueryChange(it) })
    }
}
