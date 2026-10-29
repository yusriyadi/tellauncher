package id.tellolabs.launcher

import android.graphics.Typeface
import android.os.Build
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.TextClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Screen { Home, Drawer, Settings, Hidden, ShelfEditor, ProtectedApps }

@Composable
fun LauncherUi(
    model: LauncherModel,
    backupModel: SettingsBackupModel,
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
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    val preferenceError by model.preferenceError.collectAsStateWithLifecycle()
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
                Screen.Drawer -> {
                    val visibleApps = remember(apps, preferences, query) {
                        val search = query.trim()
                        apps.orEmpty().filter { app ->
                            if (search.isEmpty()) {
                                val prefs = preferences
                                if (prefs?.customShelf == true && prefs.activeShelfId != ALL_APPS_SHELF_ID) {
                                    app.packageName in prefs.activeShelf?.packages.orEmpty()
                                } else {
                                    app.packageName !in prefs?.hiddenPackages.orEmpty()
                                }
                            } else {
                                app.label.contains(search, ignoreCase = true)
                            }
                        }
                    }
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                        val prefs = preferences
                        if (prefs?.customShelf == true && prefs.shelves.isNotEmpty()) {
                            DrawerShelfPicker(prefs) { id ->
                                keyboard?.hide()
                                query = ""
                                model.setActiveShelf(id)
                            }
                        }
                        val swipeEnabled = prefs?.customShelf == true && prefs.shelves.isNotEmpty() &&
                            prefs.swipeToSwitchShelves && query.isBlank()
                        val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
                        Box(Modifier.weight(1f).fillMaxWidth().then(
                            if (swipeEnabled) Modifier.pointerInput(prefs?.activeShelfId, swipeThreshold) {
                                var distance = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { distance = 0f },
                                    onDragCancel = { distance = 0f },
                                    onDragEnd = {
                                        when {
                                            distance <= -swipeThreshold -> model.switchShelf(1)
                                            distance >= swipeThreshold -> model.switchShelf(-1)
                                        }
                                        distance = 0f
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        distance += amount
                                    },
                                )
                            } else Modifier,
                        )) {
                            when {
                                preferenceError -> EmptyMessage(R.string.preferences_error)
                                appError -> EmptyMessage(R.string.load_error)
                                apps == null || preferences == null -> EmptyMessage(R.string.loading)
                                else -> key(prefs?.activeShelfId) {
                                    AppCollection(
                                        apps = visibleApps,
                                        grid = preferences?.grid == true,
                                        showIcons = preferences?.listIcons == true,
                                        showSettings = preferences?.showLauncherSettings != false,
                                        emptyMessage = if (query.isBlank()) R.string.no_apps else R.string.no_results,
                                        onLaunch = { app -> keyboard?.hide(); onLaunch(app) { home() } },
                                        onHide = { app ->
                                            val shelf = if (prefs?.customShelf == true) prefs.activeShelf else null
                                            if (shelf != null) model.removeFromShelf(shelf.id, app.packageName)
                                            else model.setHidden(app.packageName, true)
                                        },
                                        onAppInfo = { keyboard?.hide(); onAppInfo(it) },
                                        onSettings = { keyboard?.hide(); screen = Screen.Settings },
                                    )
                                }
                            }
                        }
                        SearchField(query, { query = it })
                    }
                }
                Screen.Settings -> Column(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    PageHeader(R.string.settings, ::home)
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
                                ) { hiddenQuery = ""; screen = Screen.Hidden }
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
                                            protectedQuery = ""
                                            screen = Screen.ProtectedApps
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
                                        if (enabled && !lockAccessibilityEnabled) showAccessibilityDialog = true
                                    },
                                )
                                if (lockSupported && prefs.doubleTapLock && !lockAccessibilityEnabled) {
                                    TextButton(onClick = { showAccessibilityDialog = true }) {
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
                                    stringResource(if (defaultHome) R.string.default_active else R.string.default_launcher),
                                    stringResource(R.string.default_hint),
                                    enabled = !defaultHome,
                                    onClick = onDefaultHome,
                                )
                            }
                        }
                    }
                }
                Screen.ShelfEditor -> if (preferences == null) {
                    EmptyMessage(if (preferenceError) R.string.preferences_error else R.string.loading)
                } else ShelfEditorScreen(
                    initialShelf = preferences?.shelves?.find { it.id == editingShelfId },
                    activateOnSave = activateNewShelf,
                    apps = apps,
                    appError = appError,
                    onBack = { keyboard?.hide(); screen = Screen.Settings },
                    onSave = { shelf, activate -> model.saveShelf(shelf, activate) },
                    onDelete = { model.deleteShelf(it) },
                )
                Screen.ProtectedApps -> Column(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
                ) {
                    PageHeader(R.string.protected_apps) {
                        keyboard?.hide()
                        onLockAuthenticationSettings()
                        screen = Screen.Settings
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
                    SearchField(protectedQuery, { protectedQuery = it }, enabled = authenticationSettingsUnlocked)
                }
                Screen.Hidden -> Column(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
                ) {
                    PageHeader(R.string.hidden_apps) { keyboard?.hide(); screen = Screen.Settings }
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
                    SearchField(hiddenQuery, { hiddenQuery = it })
                }
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

@Composable
private fun HomeScreen(
    showSeconds: Boolean,
    showDrawer: Boolean,
    doubleTapEnabled: Boolean,
    onAlarm: () -> Unit,
    onCalendar: () -> Unit,
    onDrawer: () -> Unit,
    onSettings: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val drawerLabel = stringResource(R.string.open_drawer)
    val settingsLabel = stringResource(R.string.settings)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnAlarm by rememberUpdatedState(onAlarm)
    val currentOnCalendar by rememberUpdatedState(onCalendar)
    val currentOnSettings by rememberUpdatedState(onSettings)
    val tapTargets = remember { HomeTapTargets() }
    val alarmLabel = stringResource(R.string.open_alarm)
    val calendarLabel = stringResource(R.string.open_calendar)
    val clockColor = MaterialTheme.colorScheme.primary.toArgb()
    val dateColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val locale = LocalConfiguration.current.locales[0]
    val format12 = remember(locale, showSeconds) {
        DateFormat.getBestDateTimePattern(locale, if (showSeconds) "hms" else "hm")
    }
    val format24 = remember(locale, showSeconds) {
        DateFormat.getBestDateTimePattern(locale, if (showSeconds) "Hms" else "Hm")
    }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { tapTargets.homeOffset = it.positionInRoot() }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(drawerLabel) { onDrawer(); true },
                    CustomAccessibilityAction(settingsLabel) { onSettings(); true },
                )
                contentDescription = drawerLabel
            }
            .pointerInput(doubleTapEnabled) {
                detectTapGestures(
                    onLongPress = { currentOnSettings() },
                    // One recognizer delays a single tap until double-tap detection finishes.
                    onDoubleTap = if (doubleTapEnabled) ({ currentOnDoubleTap() }) else null,
                    onTap = { position ->
                        val rootPosition = position + tapTargets.homeOffset
                        when {
                            tapTargets.clock.contains(rootPosition) -> currentOnAlarm()
                            tapTargets.date.contains(rootPosition) -> currentOnCalendar()
                        }
                    },
                )
            }
            .pointerInput(swipeThreshold) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { change, amount -> change.consume(); drag += amount },
                    onDragEnd = { if (drag < -swipeThreshold) onDrawer() },
                    onDragCancel = { drag = 0f },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AndroidView(
                    factory = { context ->
                        TextClock(context).apply {
                            textSize = 28f
                            setTextColor(clockColor)
                            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                            gravity = Gravity.CENTER
                            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        }
                    },
                    update = { clock ->
                        clock.setTextColor(clockColor)
                        if (clock.format12Hour?.toString() != format12) clock.format12Hour = format12
                        if (clock.format24Hour?.toString() != format24) clock.format24Hour = format24
                    },
                    modifier = Modifier.wrapContentSize()
                        .onGloballyPositioned { tapTargets.clock = it.boundsInRoot() }
                        .semantics {
                            onClick(label = alarmLabel) { currentOnAlarm(); true }
                        },
                )
                Spacer(Modifier.height(8.dp))
                AndroidView(
                    factory = { context ->
                        TextClock(context).apply {
                            // TextClock localizes weekday names and updates at midnight/time-zone changes.
                            format12Hour = "EEEE, dd-MM-yyyy"
                            format24Hour = "EEEE, dd-MM-yyyy"
                            textSize = 14f
                            setTextColor(dateColor)
                            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                            gravity = Gravity.CENTER
                            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        }
                    },
                    update = { date -> date.setTextColor(dateColor) },
                    modifier = Modifier.wrapContentSize()
                        .onGloballyPositioned { tapTargets.date = it.boundsInRoot() }
                        .semantics {
                            onClick(label = calendarLabel) { currentOnCalendar(); true }
                        },
                )
            }
            if (showDrawer) {
                IconButton(
                    onClick = onDrawer,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).size(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_app_drawer), contentDescription = drawerLabel,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

private class HomeTapTargets {
    var homeOffset: Offset = Offset.Zero
    var clock: Rect = Rect.Zero
    var date: Rect = Rect.Zero
}

@Composable
private fun AppCollection(
    apps: List<LauncherApp>,
    grid: Boolean,
    showIcons: Boolean,
    showSettings: Boolean,
    emptyMessage: Int,
    onLaunch: (LauncherApp) -> Unit,
    onHide: (LauncherApp) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
    onSettings: () -> Unit,
) {
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        ) {
            if (apps.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(emptyMessage), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp))
                }
            }
            items(apps, key = { it.key }) { app ->
                AppItemActions(app, onLaunch, onHide, onAppInfo) { interaction ->
                    Column(
                        Modifier.fillMaxWidth().then(interaction).padding(horizontal = 8.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app, Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(app.label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            if (showSettings) {
                item(key = "tellauncher_settings") {
                    Column(
                        Modifier.fillMaxWidth().clickable(onClick = onSettings)
                            .padding(horizontal = 8.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(painterResource(R.drawable.ic_launcher), contentDescription = null,
                            modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.launcher_settings), fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 20.dp)) {
            if (apps.isEmpty()) {
                item {
                    Text(stringResource(emptyMessage), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp))
                }
            }
            items(apps, key = { it.key }) { app ->
                AppItemActions(app, onLaunch, onHide, onAppInfo) { interaction ->
                    Row(
                        Modifier.fillMaxWidth().then(interaction)
                            .heightIn(min = 60.dp).padding(horizontal = 28.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showIcons) {
                            AppIcon(app, Modifier.size(28.dp))
                            Spacer(Modifier.width(20.dp))
                        }
                        Text(app.label, fontSize = 18.sp, fontWeight = FontWeight.Normal)
                    }
                }
            }
            if (showSettings) {
                item(key = "tellauncher_settings") {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onSettings)
                            .heightIn(min = 60.dp).padding(horizontal = 28.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showIcons) {
                            Image(painterResource(R.drawable.ic_launcher), contentDescription = null,
                                modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(20.dp))
                        }
                        Text(stringResource(R.string.launcher_settings), fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItemActions(
    app: LauncherApp,
    onLaunch: (LauncherApp) -> Unit,
    onHide: (LauncherApp) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        content(Modifier.combinedClickable(
            onClick = { onLaunch(app) },
            onLongClickLabel = stringResource(R.string.app_actions),
            onLongClick = { expanded = true },
        ))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.hide_app)) },
                onClick = { expanded = false; onHide(app) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_info)) },
                onClick = { expanded = false; onAppInfo(app) },
            )
        }
    }
}

@Composable
internal fun AppIcon(app: LauncherApp, modifier: Modifier) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { 48.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, app, pixels) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getActivityIcon(app.component).toBitmap(pixels, pixels).asImageBitmap()
            } catch (_: Exception) { null }
        }
    }
    val image = bitmap
    if (image != null) Image(image, contentDescription = null, modifier = modifier)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)))
}

@Composable
internal fun SearchField(query: String, onQuery: (String) -> Unit, enabled: Boolean = true) {
    val clearLabel = stringResource(R.string.clear_search)
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_apps), fontSize = 15.sp) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQuery("") }, enabled = enabled) {
                Text("×", fontSize = 24.sp, modifier = Modifier.semantics {
                    contentDescription = clearLabel
                })
            } }
        } else null,
    )
}

@Composable
internal fun PageHeader(title: Int, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val backLabel = stringResource(R.string.back)
        IconButton(onClick = onBack) {
            Text("‹", fontSize = 32.sp, modifier = Modifier.semantics { contentDescription = backLabel })
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(title), fontSize = 20.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
internal fun ToggleSetting(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled,
            role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SettingLink(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = 56.dp).padding(vertical = 8.dp)) {
        Text(title, fontSize = 16.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun EmptyMessage(message: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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

@Composable
private fun DrawerShelfPicker(preferences: LauncherPreferences, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val allApps = stringResource(R.string.all_apps)
    val options = listOf(ALL_APPS_SHELF_ID to allApps) + preferences.shelves.map { it.id to it.name }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        TextButton(onClick = { expanded = true }) {
            Text(preferences.activeShelf?.name ?: allApps, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                val active = preferences.activeShelfId == id
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = active },
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { RadioButton(selected = active, onClick = null) },
                    onClick = { expanded = false; onSelect(id) },
                )
            }
        }
    }
}
