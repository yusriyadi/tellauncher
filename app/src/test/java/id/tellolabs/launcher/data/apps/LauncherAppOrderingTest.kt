package id.tellolabs.launcher.data.apps

import id.tellolabs.launcher.model.LauncherApp
import id.tellolabs.launcher.testing.launcherApp
import java.text.Collator
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class LauncherAppOrderingTest {
    private val collator = Collator.getInstance(Locale.US).apply { strength = Collator.PRIMARY }

    private fun ordered(vararg apps: LauncherApp) = sortLauncherApps(apps.asSequence(), collator)

    @Test fun sortsAlphabeticallyIgnoringCaseAndAccents() {
        val alpha = launcherApp("com.alpha", "áLpha")
        val beta = launcherApp("com.beta", "beta")
        val zulu = launcherApp("com.zulu", "Zulu")
        assertEquals(listOf(alpha, beta, zulu), ordered(zulu, beta, alpha))
    }

    @Test fun equivalentLabelsHaveDeterministicComponentOrder() {
        val first = launcherApp("com.a", "Café")
        val second = launcherApp("com.z", "CAFE")
        assertEquals(listOf(first, second), ordered(second, first))
        assertEquals(listOf(first, second), ordered(first, second))
    }

    @Test fun duplicateComponentAppearsOnceAndKeepsFirstEntry() {
        val original = launcherApp("com.example", "Original")
        val duplicate = launcherApp("com.example", "Duplicate")
        val result = ordered(original, duplicate)
        assertEquals(1, result.size)
        assertSame(original, result.single())
    }

    @Test fun differentActivitiesInSamePackageRemainVisible() {
        val main = launcherApp("com.example", "Example", "com.example.MainActivity")
        val secondary = launcherApp("com.example", "Example", "com.example.SecondActivity")
        assertEquals(listOf(main, secondary), ordered(secondary, main))
    }

    @Test fun emptyInventoryIsValid() {
        assertTrue(ordered().isEmpty())
    }
}
