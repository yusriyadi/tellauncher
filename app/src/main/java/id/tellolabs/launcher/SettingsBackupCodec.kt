package id.tellolabs.launcher

import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class SettingsBackup(
    val settings: LauncherPreferences,
    val exportedAt: OffsetDateTime,
    val appVersion: String,
) {
    // Protected membership is portable; authorization and enabled protection are not.
    val settingsForRestore: LauncherPreferences get() = settings.copy(appAuthentication = false)
}

class InvalidSettingsBackupException : IllegalArgumentException("Invalid settings backup")
class UnsupportedSettingsBackupException : IllegalArgumentException("Unsupported backup schema")

/** Versioned portable format. No Android file APIs or UI dependencies. */
internal object SettingsBackupCodec {
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 1024 * 1024
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

    fun encode(settings: LauncherPreferences, appVersion: String, now: OffsetDateTime = OffsetDateTime.now()): String {
        val shelves = JSONArray().apply {
            settings.shelves.forEach { shelf ->
                put(JSONObject().apply {
                    put("id", shelf.id)
                    put("name", shelf.name)
                    put("apps", JSONArray(shelf.packages.sorted()))
                })
            }
        }
        val json = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("exportedAt", now.toString())
            put("appVersion", appVersion)
            put("settings", JSONObject().apply {
                put("themeMode", settings.themeMode.storageValue)
                put("drawerLayout", if (settings.grid) "grid" else "list")
                put("showListIcons", settings.listIcons)
                put("showSeconds", settings.showSeconds)
                put("showDrawerButton", settings.showDrawer)
                put("showLauncherSettingsInDrawer", settings.showLauncherSettings)
                put("doubleTapToLock", settings.doubleTapLock)
                put("hiddenApps", JSONArray(settings.hiddenPackages.sorted()))
                put("customShelf", JSONObject().apply {
                    put("enabled", settings.customShelf)
                    put("swipeToSwitchShelves", settings.swipeToSwitchShelves)
                    put("activeShelfId", settings.activeShelfId ?: JSONObject.NULL)
                    put("shelves", shelves)
                })
                put("appAuthentication", JSONObject().apply {
                    put("enabled", settings.appAuthentication)
                    put("protectedApps", JSONArray(settings.protectedPackages.sorted()))
                })
            })
        }.toString(2)
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw InvalidSettingsBackupException()
        return json
    }

    fun decode(json: String): SettingsBackup {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw InvalidSettingsBackupException()
        try {
            val tokens = JSONTokener(json.removePrefix("\uFEFF"))
            val root = tokens.nextValue() as? JSONObject ?: invalid()
            require(tokens.nextClean() == '\u0000')
            val version = root.opt("schemaVersion")
            require(version is Int)
            if (version != SCHEMA_VERSION) throw UnsupportedSettingsBackupException()
            val exportedAt = OffsetDateTime.parse(root.requiredString("exportedAt"))
            val appVersion = root.requiredString("appVersion")
            val values = root.getJSONObject("settings")
            val themeValue = values.requiredString("themeMode")
            val theme = ThemeMode.entries.find { it.storageValue == themeValue } ?: invalid()
            val layout = values.requiredString("drawerLayout")
            require(layout == "list" || layout == "grid")
            val customShelf = values.getJSONObject("customShelf")
            val shelfArray = customShelf.getJSONArray("shelves")
            val shelves = List(shelfArray.length()) { index ->
                val item = shelfArray.getJSONObject(index)
                Shelf(item.requiredString("id"), item.requiredString("name").trim(), item.packages("apps"))
            }
            require(shelves.map { it.id }.toSet().size == shelves.size)
            require(shelves.none { it.id == ALL_APPS_SHELF_ID })
            val enabled = customShelf.requiredBoolean("enabled")
            require(!enabled || shelves.isNotEmpty())
            require(customShelf.has("activeShelfId"))
            val activeId = if (customShelf.isNull("activeShelfId")) null
                else customShelf.requiredString("activeShelfId")
            require(activeId == null || activeId == ALL_APPS_SHELF_ID || shelves.any { it.id == activeId })
            require(!enabled || activeId != null)
            val authentication = values.getJSONObject("appAuthentication")
            return SettingsBackup(
                settings = LauncherPreferences(
                    grid = layout == "grid",
                    listIcons = values.requiredBoolean("showListIcons"),
                    showSeconds = values.requiredBoolean("showSeconds"),
                    showDrawer = values.requiredBoolean("showDrawerButton"),
                    showLauncherSettings = values.requiredBoolean("showLauncherSettingsInDrawer"),
                    doubleTapLock = values.requiredBoolean("doubleTapToLock"),
                    hiddenPackages = values.packages("hiddenApps"),
                    themeMode = theme,
                    shelves = shelves,
                    customShelf = enabled,
                    swipeToSwitchShelves = if (customShelf.has("swipeToSwitchShelves"))
                        customShelf.requiredBoolean("swipeToSwitchShelves") else false,
                    activeShelfId = activeId,
                    appAuthentication = authentication.requiredBoolean("enabled"),
                    protectedPackages = authentication.packages("protectedApps"),
                ),
                exportedAt = exportedAt,
                appVersion = appVersion,
            )
        } catch (unsupported: UnsupportedSettingsBackupException) {
            throw unsupported
        } catch (_: Exception) {
            throw InvalidSettingsBackupException()
        }
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean = opt(key) as? Boolean ?: invalid()
    private fun JSONObject.requiredString(key: String): String =
        (opt(key) as? String)?.takeIf { it.isNotBlank() } ?: invalid()

    private fun JSONObject.packages(key: String): Set<String> {
        val array = getJSONArray(key)
        return List(array.length()) { index ->
            val name = array.opt(index) as? String ?: invalid()
            require(name == "android" || packagePattern.matches(name))
            name
        }.toSet()
    }

    private fun invalid(): Nothing = throw InvalidSettingsBackupException()
}
