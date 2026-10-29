package id.tellolabs.launcher.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class LauncherScreenTest {
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any) = value is String
    }

    @Test fun everyScreenSavesAsPortableStringAndRestores() {
        val expectedIds = listOf("home", "drawer", "settings", "hidden", "shelf_editor", "protected_apps")
        assertEquals(expectedIds, Screen.entries.map { screen -> with(ScreenSaver) { scope.save(screen) } })
        Screen.entries.forEach { screen ->
            val saved = with(ScreenSaver) { scope.save(screen) }
            assertEquals(screen, ScreenSaver.restore(saved!!))
        }
    }

    @Test fun removedOrUnknownRouteSafelyOpensHome() {
        assertEquals(Screen.Home, ScreenSaver.restore("removed_route"))
        assertEquals(Screen.Home, ScreenSaver.restore(""))
    }
}
