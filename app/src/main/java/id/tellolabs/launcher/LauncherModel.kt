package id.tellolabs.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Application.preferences by preferencesDataStore("launcher_settings")
private val gridKey = booleanPreferencesKey("grid")
private val iconsKey = booleanPreferencesKey("list_icons")
private val hiddenKey = stringSetPreferencesKey("hidden_packages")
private val doubleTapLockKey = booleanPreferencesKey("double_tap_lock")

data class LauncherPreferences(
    val grid: Boolean = false,
    val listIcons: Boolean = false,
    val hiddenPackages: Set<String> = emptySet(),
    val doubleTapLock: Boolean = false,
)

data class LauncherApp(val component: ComponentName, val label: String) {
    val packageName: String get() = component.packageName
    val key: String get() = component.flattenToString()
}

class LauncherModel(application: Application) : AndroidViewModel(application) {
    private val store = application.preferences
    private val messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val errors = messages.asSharedFlow()
    private val mutablePreferenceError = MutableStateFlow(false)
    val preferenceError = mutablePreferenceError.asStateFlow()
    val preferences = store.data.map { values ->
        LauncherPreferences(
            grid = values[gridKey] ?: false,
            listIcons = values[iconsKey] ?: false,
            hiddenPackages = values[hiddenKey].orEmpty(),
            doubleTapLock = values[doubleTapLockKey] ?: false,
        )
    }.catch {
        // Keep the drawer closed if saved hidden-app preferences cannot be read.
        mutablePreferenceError.value = true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableApps = MutableStateFlow<List<LauncherApp>?>(null)
    val apps = mutableApps.asStateFlow()
    private val mutableAppError = MutableStateFlow(false)
    val appError = mutableAppError.asStateFlow()
    private var refreshJob: Job? = null

    fun refreshApps() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val manager = app.packageManager
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    @Suppress("DEPRECATION")
                    val activities = manager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
                    activities.asSequence()
                        .filter { it.activityInfo.packageName != app.packageName && it.activityInfo.enabled }
                        .map {
                            LauncherApp(
                                ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                                it.loadLabel(manager).toString(),
                            )
                        }.distinctBy { it.key }
                        .sortedWith { left, right ->
                            collator.compare(left.label, right.label).takeIf { it != 0 }
                                ?: left.key.compareTo(right.key)
                        }.toList()
                }
                mutableApps.value = loaded
                mutableAppError.value = false
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableAppError.value = true
            }
        }
    }

    fun setGrid(value: Boolean) = save { it[gridKey] = value }
    fun setListIcons(value: Boolean) = save { it[iconsKey] = value }
    fun setDoubleTapLock(value: Boolean) = save { it[doubleTapLockKey] = value }
    fun setHidden(packageName: String, hidden: Boolean) = save {
        val packages = it[hiddenKey].orEmpty()
        it[hiddenKey] = if (hidden) packages + packageName else packages - packageName
    }

    private fun save(update: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch {
            try {
                store.edit { update(it) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages.emit(R.string.save_error)
            }
        }
    }
}
