package id.tellolabs.launcher.testing

import android.content.ComponentName
import id.tellolabs.launcher.model.LauncherApp
import io.mockk.every
import io.mockk.mockk

/** Only the platform component is mocked; model properties use their real implementation. */
internal fun launcherApp(
    packageName: String,
    label: String,
    activity: String = "$packageName.MainActivity",
): LauncherApp {
    val component = mockk<ComponentName>()
    every { component.packageName } returns packageName
    every { component.flattenToString() } returns "$packageName/$activity"
    return LauncherApp(component, label)
}
