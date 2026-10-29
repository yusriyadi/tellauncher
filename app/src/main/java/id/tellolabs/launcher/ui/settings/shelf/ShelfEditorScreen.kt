package id.tellolabs.launcher.ui.settings.shelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tellolabs.launcher.R
import id.tellolabs.launcher.data.preferences.Shelf
import id.tellolabs.launcher.model.LauncherApp
import id.tellolabs.launcher.ui.components.AppIcon
import id.tellolabs.launcher.ui.components.EmptyMessage
import id.tellolabs.launcher.ui.components.PageHeader
import id.tellolabs.launcher.ui.components.SearchField
import id.tellolabs.launcher.ui.components.ToggleSetting
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
internal fun ShelfEditorScreen(
    initialShelf: Shelf?,
    activateOnSave: Boolean,
    apps: List<LauncherApp>?,
    appError: Boolean,
    onBack: () -> Unit,
    onSave: suspend (Shelf, Boolean) -> Boolean,
    onDelete: suspend (String) -> Boolean,
) {
    val shelfId = rememberSaveable { initialShelf?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(initialShelf?.name.orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val compactSearch = searchFocused && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    var selectedPackages by rememberSaveable {
        mutableStateOf(ArrayList(initialShelf?.packages.orEmpty()))
    }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val packages = remember(apps) { apps.orEmpty().distinctBy { it.packageName } }
    val selected = remember(packages, selectedPackages) {
        val membership = selectedPackages.toSet()
        packages.filter { it.packageName in membership }
    }
    val unselected = remember(packages, selectedPackages) {
        val membership = selectedPackages.toSet()
        packages.filterNot { it.packageName in membership }
    }
    val visibleSelected = remember(selected, query) {
        selected.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val visibleUnselected = remember(unselected, query) {
        unselected.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    BackHandler { if (!saving) onBack() }

    fun setSelected(packageName: String, enabled: Boolean) {
        selectedPackages = ArrayList(
            if (enabled) (selectedPackages + packageName).distinct()
            else selectedPackages - packageName,
        )
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        PageHeader(
            title = if (initialShelf == null) R.string.add_shelf else R.string.edit_shelf,
            actions = {
                if (initialShelf != null) {
                    IconButton(
                        onClick = { keyboard?.hide(); confirmDelete = true },
                        enabled = !saving,
                    ) {
                        Icon(painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.delete_shelf))
                    }
                }
                IconButton(
                    enabled = name.isNotBlank() && !saving && apps != null && !appError,
                    onClick = {
                        saving = true
                        keyboard?.hide()
                        scope.launch {
                            try {
                                if (onSave(Shelf(shelfId, name.trim(), selectedPackages.toSet()), activateOnSave)) onBack()
                            } finally { saving = false }
                        }
                    },
                ) {
                    Icon(painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(if (saving) R.string.saving else R.string.save))
                }
            },
            onBack = { if (!saving) onBack() },
        )
        if (!compactSearch) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.shelf_name)) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                appError -> EmptyMessage(R.string.load_error)
                apps == null -> EmptyMessage(R.string.loading)
                packages.isEmpty() -> EmptyMessage(R.string.no_apps)
                visibleSelected.isEmpty() && visibleUnselected.isEmpty() -> EmptyMessage(R.string.no_results)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    if (query.isBlank() || visibleSelected.isNotEmpty()) {
                        item(key = "selected_heading", contentType = "heading") {
                            ShelfSectionTitle(stringResource(R.string.selected_apps, selected.size))
                        }
                    }
                    if (visibleSelected.isEmpty() && query.isBlank()) {
                        item(key = "selected_empty", contentType = "hint") {
                            Text(stringResource(R.string.no_selected_apps),
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    items(visibleSelected, key = { it.packageName }, contentType = { "app" }) { app ->
                        ToggleSetting(
                            title = app.label, checked = true, enabled = !saving,
                            onChange = { setSelected(app.packageName, it) },
                            leadingIcon = { AppIcon(app, Modifier.size(28.dp)) },
                        )
                    }
                    if (query.isBlank() || (visibleSelected.isNotEmpty() && visibleUnselected.isNotEmpty())) {
                        item(key = "all_heading", contentType = "heading") {
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            ShelfSectionTitle(stringResource(R.string.all_apps))
                        }
                    }
                    items(visibleUnselected, key = { it.packageName }, contentType = { "app" }) { app ->
                        ToggleSetting(
                            title = app.label, checked = false, enabled = !saving,
                            onChange = { setSelected(app.packageName, it) },
                            leadingIcon = { AppIcon(app, Modifier.size(28.dp)) },
                        )
                    }
                }
            }
        }
        SearchField(
            query, { query = it }, enabled = !saving,
            modifier = Modifier.onFocusChanged { searchFocused = it.isFocused },
        )

    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!saving) confirmDelete = false },
            title = { Text(stringResource(R.string.delete_shelf)) },
            text = { Text(stringResource(R.string.delete_shelf_message, initialShelf?.name.orEmpty())) },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    scope.launch {
                        saving = true
                        try {
                            if (onDelete(shelfId)) {
                                confirmDelete = false
                                onBack()
                            }
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ShelfSectionTitle(title: String) {
    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 8.dp))
}
