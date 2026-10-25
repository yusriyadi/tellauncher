package id.tellolabs.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LauncherApp(val component: ComponentName, val label: String) {
    val packageName: String get() = component.packageName
    val key: String get() = component.flattenToString()
}

class LauncherModel(application: Application) : AndroidViewModel(application) {
    private val repository = LauncherPreferencesRepository(application)
    private val messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val errors = messages.asSharedFlow()
    private val mutablePreferenceError = MutableStateFlow(false)
    val preferenceError = mutablePreferenceError.asStateFlow()
    val preferences = repository.preferences.catch {
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

    fun setGrid(value: Boolean) = save { repository.setGrid(value) }
    fun setListIcons(value: Boolean) = save { repository.setListIcons(value) }
    fun setDoubleTapLock(value: Boolean) = save { repository.setDoubleTapLock(value) }
    fun setHidden(packageName: String, hidden: Boolean) = save { repository.setHidden(packageName, hidden) }
    fun setCustomShelf(enabled: Boolean) = save { repository.setCustomShelf(enabled) }
    fun setActiveShelf(id: String) = save { repository.setActiveShelf(id) }
    suspend fun saveShelf(shelf: Shelf, activate: Boolean): Boolean = persist { repository.saveShelf(shelf, activate) }
    suspend fun deleteShelf(id: String): Boolean = persist { repository.deleteShelf(id) }

    private fun save(update: suspend () -> Unit) {
        viewModelScope.launch { persist(update) }
    }

    private suspend fun persist(update: suspend () -> Unit): Boolean = try {
        update()
        true
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        messages.emit(R.string.save_error)
        false
    }
}
