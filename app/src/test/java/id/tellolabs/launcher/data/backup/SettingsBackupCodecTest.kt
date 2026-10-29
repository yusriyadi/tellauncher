package id.tellolabs.launcher.data.backup

import id.tellolabs.launcher.data.preferences.ALL_APPS_SHELF_ID
import id.tellolabs.launcher.data.preferences.LauncherPreferences
import id.tellolabs.launcher.data.preferences.Shelf
import id.tellolabs.launcher.ui.theme.ThemeMode
import java.time.OffsetDateTime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SettingsBackupCodecTest {
    private val exportedAt = OffsetDateTime.parse("2026-10-27T09:00:00+07:00")
    private val settings = LauncherPreferences(
        grid = true, listIcons = true, hiddenPackages = setOf("com.hidden.app"),
        doubleTapLock = true, showSeconds = true, showDrawer = true, showLauncherSettings = false,
        themeMode = ThemeMode.NIGHT, appAuthentication = true,
        protectedPackages = setOf("com.not.installed"),
        shelves = listOf(Shelf("fav", "Fav", setOf("com.example.app", "com.not.installed"))),
        customShelf = true, activeShelfId = "fav", swipeToSwitchShelves = true,
    )
    private fun encoded() = SettingsBackupCodec.encode(settings, "0.4.1", exportedAt)

    @Test fun roundTripPreservesEveryPreferenceAndMissingApps() {
        val backup = SettingsBackupCodec.decode(encoded())
        assertEquals(settings, backup.settings)
        assertEquals(exportedAt, backup.exportedAt)
        assertEquals("0.4.1", backup.appVersion)
    }

    @Test fun restoreDisablesAuthenticationButKeepsProtectedMembership() {
        val backup = SettingsBackupCodec.decode(encoded())
        assertTrue(backup.settings.appAuthentication)
        assertEquals(settings.copy(appAuthentication = false), backup.settingsForRestore)
    }

    @Test fun allAppsSelectionSurvivesRoundTrip() {
        val original = settings.copy(activeShelfId = ALL_APPS_SHELF_ID)
        assertEquals(original, SettingsBackupCodec.decode(
            SettingsBackupCodec.encode(original, "0.4.1", exportedAt)).settings)
    }

    @Test fun shelfOrderSurvivesBackup() {
        val original = settings.copy(shelves = listOf(
            Shelf("works", "Works", emptySet()), settings.shelves.first(),
            Shelf("focus", "Focus", emptySet()),
        ))
        assertEquals(original, SettingsBackupCodec.decode(
            SettingsBackupCodec.encode(original, "0.4.1", exportedAt)).settings)
    }

    @Test fun olderBackupDefaultsSwipeToOff() {
        val json = JSONObject(encoded())
        val custom = json.getJSONObject("settings").getJSONObject("customShelf")
        custom.remove("swipeToSwitchShelves")
        assertFalse(SettingsBackupCodec.decode(json.toString()).settings.swipeToSwitchShelves)
        custom.put("swipeToSwitchShelves", "true")
        assertInvalid(json.toString())
    }

    @Test fun futureSchemaIsRejected() {
        val json = JSONObject(encoded()).put("schemaVersion", 2)
        assertThrows(UnsupportedSettingsBackupException::class.java) { SettingsBackupCodec.decode(json.toString()) }
    }

    @Test fun stringBooleansAndMissingFieldsAreRejected() {
        val json = JSONObject(encoded())
        json.getJSONObject("settings").put("showSeconds", "true")
        assertInvalid(json.toString())
        json.getJSONObject("settings").remove("showSeconds")
        assertInvalid(json.toString())
    }

    @Test fun invalidActiveShelfAndDuplicateIdsAreRejected() {
        val json = JSONObject(encoded())
        val custom = json.getJSONObject("settings").getJSONObject("customShelf")
        custom.put("activeShelfId", "missing")
        assertInvalid(json.toString())
        custom.put("activeShelfId", "fav")
        val shelves = custom.getJSONArray("shelves")
        shelves.put(shelves.getJSONObject(0))
        assertInvalid(json.toString())
    }

    @Test fun invalidPackageAndInvalidThemeAreRejected() {
        val json = JSONObject(encoded())
        json.getJSONObject("settings").getJSONArray("hiddenApps").put("Not a package name")
        assertInvalid(json.toString())
        val theme = JSONObject(encoded())
        theme.getJSONObject("settings").put("themeMode", "unknown")
        assertInvalid(theme.toString())
    }

    @Test fun truncatedTrailingAndOversizedFilesAreRejected() {
        assertInvalid(encoded().dropLast(1))
        assertInvalid(encoded() + " trailing garbage")
        assertInvalid(" ".repeat(SettingsBackupCodec.MAX_BYTES + 1))
    }

    @Test fun defaultSettingsWithNoShelvesAreValid() {
        val defaults = LauncherPreferences()
        assertEquals(defaults, SettingsBackupCodec.decode(
            SettingsBackupCodec.encode(defaults, "0.4.1", exportedAt)).settings)
    }

    private fun assertInvalid(json: String) {
        assertThrows(InvalidSettingsBackupException::class.java) { SettingsBackupCodec.decode(json) }
    }
}
