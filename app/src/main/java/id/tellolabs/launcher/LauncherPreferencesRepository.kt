package id.tellolabs.launcher

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.launcherDataStore by preferencesDataStore("launcher_settings")
private val gridKey = booleanPreferencesKey("grid")
private val iconsKey = booleanPreferencesKey("list_icons")
private val hiddenKey = stringSetPreferencesKey("hidden_packages")
private val doubleTapLockKey = booleanPreferencesKey("double_tap_lock")
private val showSecondsKey = booleanPreferencesKey("show_seconds")
private val showDrawerKey = booleanPreferencesKey("show_drawer")
private val showLauncherSettingsKey = booleanPreferencesKey("show_launcher_settings_in_drawer")
private val themeModeKey = stringPreferencesKey("theme_mode")
private val appAuthenticationKey = booleanPreferencesKey("app_authentication")
private val protectedPackagesKey = stringSetPreferencesKey("protected_packages")
private val shelvesKey = stringPreferencesKey("shelves")
private val customShelfKey = booleanPreferencesKey("custom_shelf_enabled")
private val activeShelfKey = stringPreferencesKey("active_shelf_id")
internal const val ALL_APPS_SHELF_ID = "__all_apps__"

data class Shelf(val id: String, val name: String, val packages: Set<String>)

data class LauncherPreferences(
    val grid: Boolean = false,
    val listIcons: Boolean = false,
    val hiddenPackages: Set<String> = emptySet(),
    val doubleTapLock: Boolean = false,
    val showSeconds: Boolean = false,
    val showDrawer: Boolean = false,
    val showLauncherSettings: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appAuthentication: Boolean = false,
    val protectedPackages: Set<String> = emptySet(),
    val shelves: List<Shelf> = emptyList(),
    val customShelf: Boolean = false,
    val activeShelfId: String? = null,
) {
    val activeShelf: Shelf? get() = shelves.find { it.id == activeShelfId }
}

/** Stores shelf membership and active selection together, using the existing preferences file. */
class LauncherPreferencesRepository(context: Context) {
    private val store = context.applicationContext.launcherDataStore

    val preferences = store.data.map { values ->
        val shelves = decodeShelves(values[shelvesKey])
        LauncherPreferences(
            grid = values[gridKey] ?: false,
            listIcons = values[iconsKey] ?: false,
            hiddenPackages = values[hiddenKey].orEmpty(),
            doubleTapLock = values[doubleTapLockKey] ?: false,
            showSeconds = values[showSecondsKey] ?: false,
            showDrawer = values[showDrawerKey] ?: false,
            showLauncherSettings = values[showLauncherSettingsKey] ?: true,
            themeMode = ThemeMode.entries.find { it.storageValue == values[themeModeKey] } ?: ThemeMode.SYSTEM,
            appAuthentication = values[appAuthenticationKey] ?: false,
            protectedPackages = values[protectedPackagesKey].orEmpty(),
            shelves = shelves,
            customShelf = values[customShelfKey] == true && shelves.isNotEmpty(),
            activeShelfId = values[activeShelfKey]?.takeIf { id ->
                id == ALL_APPS_SHELF_ID || shelves.any { it.id == id }
            }
                ?: shelves.firstOrNull()?.id,
        )
    }

    suspend fun setGrid(value: Boolean) { store.edit { it[gridKey] = value } }
    suspend fun setListIcons(value: Boolean) { store.edit { it[iconsKey] = value } }
    suspend fun setDoubleTapLock(value: Boolean) { store.edit { it[doubleTapLockKey] = value } }
    suspend fun setShowSeconds(value: Boolean) { store.edit { it[showSecondsKey] = value } }
    suspend fun setShowDrawer(value: Boolean) { store.edit { it[showDrawerKey] = value } }
    suspend fun setShowLauncherSettings(value: Boolean) { store.edit { it[showLauncherSettingsKey] = value } }
    suspend fun setThemeMode(value: ThemeMode) { store.edit { it[themeModeKey] = value.storageValue } }
    suspend fun setAppAuthentication(value: Boolean) { store.edit { it[appAuthenticationKey] = value } }
    suspend fun setProtected(packageName: String, protected: Boolean) {
        store.edit {
            val packages = it[protectedPackagesKey].orEmpty()
            it[protectedPackagesKey] = if (protected) packages + packageName else packages - packageName
        }
    }
    suspend fun setHidden(packageName: String, hidden: Boolean) {
        store.edit {
            val packages = it[hiddenKey].orEmpty()
            it[hiddenKey] = if (hidden) packages + packageName else packages - packageName
        }
    }

    suspend fun setCustomShelf(enabled: Boolean) {
        store.edit {
            val shelves = decodeShelves(it[shelvesKey])
            it[customShelfKey] = enabled && shelves.isNotEmpty()
            if (shelves.isNotEmpty() && it[activeShelfKey] != ALL_APPS_SHELF_ID &&
                shelves.none { shelf -> shelf.id == it[activeShelfKey] }) {
                it[activeShelfKey] = shelves.first().id
            }
        }
    }

    suspend fun setActiveShelf(id: String) {
        store.edit {
            if (id == ALL_APPS_SHELF_ID || decodeShelves(it[shelvesKey]).any { shelf -> shelf.id == id }) {
                it[activeShelfKey] = id
            }
        }
    }

    suspend fun saveShelf(shelf: Shelf, activate: Boolean) {
        require(shelf.name.isNotBlank())
        store.edit {
            val shelves = decodeShelves(it[shelvesKey]).toMutableList()
            val index = shelves.indexOfFirst { saved -> saved.id == shelf.id }
            val saved = shelf.copy(name = shelf.name.trim())
            if (index >= 0) shelves[index] = saved else shelves.add(saved)
            it[shelvesKey] = encodeShelves(shelves)
            if (activate || (it[activeShelfKey] != ALL_APPS_SHELF_ID &&
                    shelves.none { savedShelf -> savedShelf.id == it[activeShelfKey] })) {
                it[activeShelfKey] = shelf.id
            }
            if (activate) it[customShelfKey] = true
        }
    }

    suspend fun removeFromShelf(shelfId: String, packageName: String) {
        store.edit {
            val shelves = decodeShelves(it[shelvesKey]).map { shelf ->
                if (shelf.id == shelfId) shelf.copy(packages = shelf.packages - packageName)
                else shelf
            }
            // Only shelf membership changes; global Hidden apps and other shelves stay intact.
            it[shelvesKey] = encodeShelves(shelves)
        }
    }

    suspend fun deleteShelf(id: String) {
        store.edit {
            val shelves = decodeShelves(it[shelvesKey]).filterNot { shelf -> shelf.id == id }
            it[shelvesKey] = encodeShelves(shelves)
            if (shelves.isEmpty()) {
                it[customShelfKey] = false
                it.remove(activeShelfKey)
            } else if (it[activeShelfKey] != ALL_APPS_SHELF_ID &&
                shelves.none { shelf -> shelf.id == it[activeShelfKey] }) {
                it[activeShelfKey] = shelves.first().id
            }
        }
    }
}

private fun decodeShelves(json: String?): List<Shelf> {
    if (json == null) return emptyList()
    val array = JSONArray(json)
    return List(array.length()) { index ->
        val shelf = array.getJSONObject(index)
        val packages = shelf.getJSONArray("packages")
        Shelf(shelf.getString("id"), shelf.getString("name"),
            List(packages.length()) { packages.getString(it) }.toSet())
    }
}

private fun encodeShelves(shelves: List<Shelf>): String = JSONArray().apply {
    shelves.forEach { shelf ->
        put(JSONObject().apply {
            put("id", shelf.id)
            put("name", shelf.name)
            put("packages", JSONArray(shelf.packages.sorted()))
        })
    }
}.toString()
