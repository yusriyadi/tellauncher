package id.tellolabs.launcher.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.tellolabs.launcher.R
import id.tellolabs.launcher.model.LauncherApp
import id.tellolabs.launcher.ui.components.EmptyMessage
import id.tellolabs.launcher.ui.drawer.DrawerScreen
import id.tellolabs.launcher.ui.home.HomeScreen
import id.tellolabs.launcher.ui.settings.HiddenAppsScreen
import id.tellolabs.launcher.ui.settings.ProtectedAppsScreen
import id.tellolabs.launcher.ui.settings.SettingsScreen
import id.tellolabs.launcher.ui.settings.SettingsViewModel
import id.tellolabs.launcher.ui.settings.backup.SettingsBackupViewModel
import id.tellolabs.launcher.ui.settings.backup.SettingsImportDialog
import id.tellolabs.launcher.ui.settings.shelf.ShelfEditorScreen

private enum class Screen { Home, Drawer, Settings, Hidden, ShelfEditor, ProtectedApps }

@Composable
fun LauncherUi(
    model: LauncherViewModel,
    settingsModel: SettingsViewModel,
    backupModel: SettingsBackupViewModel,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onConfirmImport: () -> Unit,
    homeRequest: Int,
    defaultHome: Boolean,
    onDefaultHome: () -> Unit,
    onLaunch: (LauncherApp, () -> Unit) -> Unit,
    authenticationSettingsUnlocked: Boolean,
    onAuthenticate: (() -> Unit) -> Unit,
    onUnlockAuthenticationSettings: (() -> Unit) -> Unit,
    onLockAuthenticationSettings: () -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
    onOpenAlarm: () -> Unit,
    onOpenCalendar: () -> Unit,
    lockAccessibilityEnabled: Boolean,
    onAccessibilitySettings: () -> Unit,
    onLockScreen: () -> Unit,
) {
    val preferences by settingsModel.preferences.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    val preferenceError by settingsModel.preferenceError.collectAsStateWithLifecycle()
    val appError by model.appError.collectAsStateWithLifecycle()
    val backupBusy by backupModel.busy.collectAsStateWithLifecycle()
    val pendingImport by backupModel.pendingImport.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var editingShelfId by rememberSaveable { mutableStateOf<String?>(null) }
    var activateNewShelf by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hiddenQuery by rememberSaveable { mutableStateOf("") }
    var protectedQuery by rememberSaveable { mutableStateOf("") }
    var showAccessibilityDialog by rememberSaveable { mutableStateOf(false) }
    val lockSupported = Build.VERSION.SDK_INT >= 28
    val keyboard = LocalSoftwareKeyboardController.current

    fun home() { keyboard?.hide(); onLockAuthenticationSettings(); query = ""; screen = Screen.Home }
    LaunchedEffect(authenticationSettingsUnlocked) {
        if (!authenticationSettingsUnlocked && screen == Screen.ProtectedApps) screen = Screen.Settings
    }
    var handledHomeRequest by rememberSaveable { mutableIntStateOf(homeRequest) }
    LaunchedEffect(homeRequest) {
        if (homeRequest != handledHomeRequest) {
            handledHomeRequest = homeRequest
            home()
        }
    }
    BackHandler(enabled = screen != Screen.Home) {
        if (screen == Screen.Hidden || screen == Screen.ShelfEditor || screen == Screen.ProtectedApps) {
            keyboard?.hide()
            onLockAuthenticationSettings()
            screen = Screen.Settings
        } else home()
    }
    // Home is the root of the launcher task; Back must not reveal another activity.
    BackHandler(enabled = screen == Screen.Home) { }

    fun editShelf(id: String?, activate: Boolean = false) {
        editingShelfId = id
        activateNewShelf = activate
        screen = Screen.ShelfEditor
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
            label = "screen",
        ) { current ->
            when (current) {
                Screen.Home -> HomeScreen(
                    showSeconds = preferences?.showSeconds == true,
                    showDrawer = preferences?.showDrawer == true,
                    doubleTapEnabled = lockSupported && preferences?.doubleTapLock == true,
                    onAlarm = onOpenAlarm,
                    onCalendar = onOpenCalendar,
                    onDrawer = { query = ""; screen = Screen.Drawer },
                    onSettings = { screen = Screen.Settings },
                    onDoubleTap = {
                        if (lockSupported && preferences?.doubleTapLock == true) {
                            if (lockAccessibilityEnabled) onLockScreen()
                            else showAccessibilityDialog = true
                        }
                    },
                )
                Screen.Drawer -> DrawerScreen(
                    model = model,
                    preferences = preferences,
                    apps = apps,
                    preferenceError = preferenceError,
                    appError = appError,
                    query = query,
                    onQueryChange = { query = it },
                    onLaunch = onLaunch,
                    onAppInfo = onAppInfo,
                    onHome = ::home,
                    onSettings = { screen = Screen.Settings },
                )
                Screen.Settings -> SettingsScreen(
                    model = settingsModel,
                    preferences = preferences,
                    preferenceError = preferenceError,
                    defaultHome = defaultHome,
                    lockSupported = lockSupported,
                    lockAccessibilityEnabled = lockAccessibilityEnabled,
                    backupBusy = backupBusy,
                    onHome = ::home,
                    onEditShelf = ::editShelf,
                    onHiddenApps = { hiddenQuery = ""; screen = Screen.Hidden },
                    onProtectedApps = { protectedQuery = ""; screen = Screen.ProtectedApps },
                    onAuthenticate = onAuthenticate,
                    onUnlockAuthenticationSettings = onUnlockAuthenticationSettings,
                    onAccessibilityRequired = { showAccessibilityDialog = true },
                    onExportSettings = onExportSettings,
                    onImportSettings = onImportSettings,
                    onDefaultHome = onDefaultHome,
                )
                Screen.ShelfEditor -> if (preferences == null) {
                    EmptyMessage(if (preferenceError) R.string.preferences_error else R.string.loading)
                } else ShelfEditorScreen(
                    initialShelf = preferences?.shelves?.find { it.id == editingShelfId },
                    activateOnSave = activateNewShelf,
                    apps = apps,
                    appError = appError,
                    onBack = { keyboard?.hide(); screen = Screen.Settings },
                    onSave = { shelf, activate -> settingsModel.saveShelf(shelf, activate) },
                    onDelete = { settingsModel.deleteShelf(it) },
                )
                Screen.ProtectedApps -> ProtectedAppsScreen(
                    model = settingsModel,
                    preferences = preferences,
                    apps = apps,
                    preferenceError = preferenceError,
                    appError = appError,
                    protectedQuery = protectedQuery,
                    onQueryChange = { protectedQuery = it },
                    authenticationSettingsUnlocked = authenticationSettingsUnlocked,
                    onLockAuthenticationSettings = onLockAuthenticationSettings,
                    onBack = { screen = Screen.Settings },
                )
                Screen.Hidden -> HiddenAppsScreen(
                    model = settingsModel,
                    preferences = preferences,
                    apps = apps,
                    preferenceError = preferenceError,
                    appError = appError,
                    hiddenQuery = hiddenQuery,
                    onQueryChange = { hiddenQuery = it },
                    onBack = { screen = Screen.Settings },
                )
            }
        }
    }
    pendingImport?.let { backup ->
        SettingsImportDialog(backup, backupBusy, onConfirmImport, backupModel::discardImport)
    }
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text(stringResource(R.string.accessibility_required)) },
            text = { Text(stringResource(R.string.accessibility_required_message, stringResource(R.string.lock_service_name))) },
            confirmButton = {
                TextButton(onClick = {
                    showAccessibilityDialog = false
                    onAccessibilitySettings()
                }) { Text(stringResource(R.string.open_accessibility_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) {
                    Text(stringResource(R.string.later))
                }
            },
        )
    }
}
