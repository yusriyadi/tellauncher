package id.tellolabs.launcher.ui

import androidx.compose.runtime.saveable.Saver

internal enum class Screen(val savedId: String) {
    Home("home"),
    Drawer("drawer"),
    Settings("settings"),
    Hidden("hidden"),
    ShelfEditor("shelf_editor"),
    ProtectedApps("protected_apps"),
}

// Persist stable IDs rather than a Serializable enum whose class name can change.
internal val ScreenSaver = Saver<Screen, String>(
    save = { it.savedId },
    restore = { id -> Screen.entries.find { it.savedId == id } ?: Screen.Home },
)
