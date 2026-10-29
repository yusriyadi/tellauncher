package id.tellolabs.launcher.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import id.tellolabs.launcher.model.LauncherApp
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(context: Context) {
    private val context = context.applicationContext

    suspend fun getApps(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val app = context
        val manager = app.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = manager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val apps = activities.asSequence()
            // The query already respects runtime component overrides. ActivityInfo.enabled
            // can still be false for a manifest-disabled alias enabled by the app later.
            .filter { it.activityInfo.packageName != app.packageName }
            .map {
                LauncherApp(
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadLabel(manager).toString(),
                )
            }
        sortLauncherApps(apps, collator)
    }
}

internal fun sortLauncherApps(apps: Sequence<LauncherApp>, collator: Collator): List<LauncherApp> =
    apps.distinctBy { it.key }
        .sortedWith { left, right ->
            collator.compare(left.label, right.label).takeIf { it != 0 }
                ?: left.key.compareTo(right.key)
        }.toList()
