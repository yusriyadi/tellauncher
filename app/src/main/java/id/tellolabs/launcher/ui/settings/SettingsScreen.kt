package id.tellolabs.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tellolabs.launcher.R
import id.tellolabs.launcher.data.preferences.ALL_APPS_SHELF_ID
import id.tellolabs.launcher.data.preferences.LauncherPreferences
import id.tellolabs.launcher.ui.components.EmptyMessage
import id.tellolabs.launcher.ui.components.PageHeader
import id.tellolabs.launcher.ui.components.SettingLink
import id.tellolabs.launcher.ui.components.ToggleSetting
import id.tellolabs.launcher.ui.settings.shelf.rememberShelfReorderState
import id.tellolabs.launcher.ui.settings.shelf.shelfDragOffset
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(
    model: SettingsViewModel,
    preferences: LauncherPreferences?,
    preferenceError: Boolean,
    defaultHome: Boolean,
    lockSupported: Boolean,
    lockAccessibilityEnabled: Boolean,
    backupBusy: Boolean,
    onHome: () -> Unit,
    onEditShelf: (String?, Boolean) -> Unit,
    onHiddenApps: () -> Unit,
    onProtectedApps: () -> Unit,
    onAuthenticate: (() -> Unit) -> Unit,
    onUnlockAuthenticationSettings: (() -> Unit) -> Unit,
    onAccessibilityRequired: () -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onDefaultHome: () -> Unit,
) {
    fun editShelf(id: String?, activate: Boolean = false) = onEditShelf(id, activate)
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PageHeader(R.string.settings, onBack = onHome)
        val prefs = preferences
        if (prefs == null) {
            EmptyMessage(if (preferenceError) R.string.preferences_error else R.string.loading)
        } else {
            val listState = rememberLazyListState()
            val shelfOrder = rememberShelfReorderState(prefs.shelves, listState)
            val scope = rememberCoroutineScope()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            ) {
                item {
                    ThemeSetting(mode = prefs.themeMode, onChange = model::setThemeMode)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
                item {
                    Text(stringResource(R.string.layout), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Row(Modifier.padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = !prefs.grid, onClick = { model.setGrid(false) }, label = { Text(stringResource(R.string.list)) })
                        FilterChip(selected = prefs.grid, onClick = { model.setGrid(true) }, label = { Text(stringResource(R.string.grid)) })
                    }
                }
                item {
                    ToggleSetting(
                        title = stringResource(R.string.list_icons),
                        subtitle = stringResource(R.string.list_icons_hint),
                        checked = prefs.listIcons,
                        enabled = !prefs.grid,
                        onChange = model::setListIcons,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
                item {
                    ToggleSetting(
                        title = stringResource(R.string.show_seconds),
                        subtitle = stringResource(R.string.show_seconds_hint),
                        checked = prefs.showSeconds,
                        onChange = model::setShowSeconds,
                    )
                }
                item {
                    ToggleSetting(
                        title = stringResource(R.string.show_drawer),
                        subtitle = stringResource(R.string.show_drawer_hint),
                        checked = prefs.showDrawer,
                        onChange = model::setShowDrawer,
                    )
                }
                item {
                    ToggleSetting(
                        title = stringResource(R.string.show_launcher_settings),
                        subtitle = stringResource(R.string.show_launcher_settings_hint),
                        checked = prefs.showLauncherSettings,
                        onChange = model::setShowLauncherSettings,
                    )
                }
                item {
                    ToggleSetting(
                        title = stringResource(R.string.custom_shelf),
                        subtitle = stringResource(R.string.custom_shelf_hint),
                        checked = prefs.customShelf,
                        onChange = { enabled ->
                            if (enabled && prefs.shelves.isEmpty()) editShelf(null, activate = true)
                            else model.setCustomShelf(enabled)
                        },
                    )
                }
                if (prefs.customShelf) {
                    item(key = "swipe_shelves") {
                        ToggleSetting(
                            title = stringResource(R.string.swipe_to_switch_shelves),
                            subtitle = stringResource(R.string.swipe_shelves_hint),
                            checked = prefs.swipeToSwitchShelves,
                            onChange = model::setSwipeToSwitchShelves,
                        )
                    }
                    item(key = "all_apps_shelf") {
                        ShelfSettingRow(
                            name = stringResource(R.string.all_apps),
                            selected = prefs.activeShelfId == ALL_APPS_SHELF_ID,
                            onSelect = { model.setActiveShelf(ALL_APPS_SHELF_ID) },
                        )
                    }
                    items(shelfOrder.shelves, key = { "shelf_${it.id}" }) { shelf ->
                        val reorderLabel = stringResource(R.string.reorder_shelf, shelf.name)
                        val moveUpLabel = stringResource(R.string.move_shelf_up, shelf.name)
                        val moveDownLabel = stringResource(R.string.move_shelf_down, shelf.name)
                        ShelfSettingRow(
                            name = shelf.name,
                            selected = prefs.activeShelfId == shelf.id,
                            onSelect = { model.setActiveShelf(shelf.id) },
                            onEdit = { editShelf(shelf.id) },
                            modifier = Modifier.shelfDragOffset(shelfOrder, shelf.id),
                            dragHandle = {
                                Box(
                                    Modifier.size(48.dp)
                                        .semantics {
                                            contentDescription = reorderLabel
                                            customActions = listOf(-1 to moveUpLabel, 1 to moveDownLabel)
                                                .mapNotNull { (direction, label) ->
                                                    val from = shelfOrder.shelves.indexOfFirst { it.id == shelf.id }
                                                    val to = from + direction
                                                    if (shelfOrder.saving || to !in shelfOrder.shelves.indices) null
                                                    else CustomAccessibilityAction(label) {
                                                        val ids = shelfOrder.shelves.map { it.id }.toMutableList()
                                                        ids.add(to, ids.removeAt(from))
                                                        scope.launch { model.reorderShelves(ids) }
                                                        true
                                                    }
                                                }
                                        }
                                        .pointerInput(shelfOrder, shelf.id) {
                                            detectDragGestures(
                                                onDragStart = { shelfOrder.start(shelf.id) },
                                                onDragCancel = { shelfOrder.cancel() },
                                                onDragEnd = { scope.launch { shelfOrder.drop(model::reorderShelves) } },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    shelfOrder.drag(amount.y)
                                                },
                                            )
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(R.drawable.ic_drag_indicator), contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                        )
                    }
                    item {
                        TextButton(onClick = { editShelf(null) }) {
                            Text(stringResource(R.string.add_shelf))
                        }
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                    SettingLink(
                        stringResource(R.string.hidden_apps),
                        stringResource(R.string.hidden_count, prefs.hiddenPackages.size),
                    ) { onHiddenApps() }
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    ToggleSetting(
                        title = stringResource(R.string.app_authentication),
                        subtitle = stringResource(R.string.app_authentication_hint),
                        checked = prefs.appAuthentication,
                        onChange = { enabled ->
                            onAuthenticate { model.setAppAuthentication(enabled) }
                        },
                    )
                    if (prefs.appAuthentication) {
                        SettingLink(
                            stringResource(R.string.protected_apps),
                            stringResource(R.string.protected_count, prefs.protectedPackages.size),
                        ) {
                            onUnlockAuthenticationSettings {
                                onProtectedApps()
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    ToggleSetting(
                        title = stringResource(R.string.double_tap_lock),
                        subtitle = stringResource(
                            if (lockSupported) R.string.double_tap_lock_hint
                            else R.string.double_tap_lock_unsupported,
                        ),
                        checked = prefs.doubleTapLock,
                        enabled = lockSupported,
                        onChange = { enabled ->
                            model.setDoubleTapLock(enabled)
                            if (enabled && !lockAccessibilityEnabled) onAccessibilityRequired()
                        },
                    )
                    if (lockSupported && prefs.doubleTapLock && !lockAccessibilityEnabled) {
                        TextButton(onClick = { onAccessibilityRequired() }) {
                            Text(stringResource(R.string.open_accessibility_settings))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.backup_restore),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp))
                    SettingLink(stringResource(R.string.backup_export),
                        stringResource(R.string.backup_export_hint), enabled = !backupBusy,
                        onClick = onExportSettings)
                    SettingLink(stringResource(R.string.backup_import),
                        stringResource(R.string.backup_import_hint), enabled = !backupBusy,
                        onClick = onImportSettings)
                    if (backupBusy) Text(stringResource(R.string.backup_working),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    SettingLink(
                        stringResource(if (defaultHome) R.string.default_change else R.string.default_launcher),
                        stringResource(if (defaultHome) R.string.default_active else R.string.default_hint),
                        onClick = onDefaultHome,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfSettingRow(
    name: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).heightIn(min = 48.dp).selectable(
            selected = selected,
            role = androidx.compose.ui.semantics.Role.RadioButton,
            onClick = onSelect,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            RadioButton(selected = selected, onClick = null)
        }
        Text(name, modifier = Modifier.weight(1f).padding(start = 8.dp), fontSize = 16.sp)
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(
                    painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.edit_shelf),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        dragHandle?.invoke()
    }
}
