package id.tellolabs.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DrawerScreen(
    model: LauncherModel,
    preferences: LauncherPreferences?,
    apps: List<LauncherApp>?,
    preferenceError: Boolean,
    appError: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onLaunch: (LauncherApp, () -> Unit) -> Unit,
    onAppInfo: (LauncherApp) -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val search = query.trim()
    val settingsLabel = stringResource(R.string.launcher_settings)
    val showSettings = if (search.isNotEmpty()) {
        settingsLabel.contains(search, ignoreCase = true)
    } else {
        preferences?.showLauncherSettings == true &&
            (preferences?.customShelf != true || preferences?.activeShelfId == ALL_APPS_SHELF_ID)
    }
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
                onQueryChange("")
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
                        showSettings = showSettings,
                        emptyMessage = if (query.isBlank()) R.string.no_apps else R.string.no_results,
                        onLaunch = { app -> keyboard?.hide(); onLaunch(app) { onHome() } },
                        onHide = { app ->
                            val shelf = if (prefs?.customShelf == true) prefs.activeShelf else null
                            if (shelf != null) model.removeFromShelf(shelf.id, app.packageName)
                            else model.setHidden(app.packageName, true)
                        },
                        onAppInfo = { keyboard?.hide(); onAppInfo(it) },
                        onSettings = { keyboard?.hide(); onSettings() },
                    )
                }
            }
        }
        SearchField(query, { onQueryChange(it) })
    }
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
