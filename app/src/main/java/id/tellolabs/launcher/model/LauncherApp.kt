package id.tellolabs.launcher.model

import android.content.ComponentName

data class LauncherApp(val component: ComponentName, val label: String) {
    val packageName: String get() = component.packageName
    val key: String get() = component.flattenToString()
}
