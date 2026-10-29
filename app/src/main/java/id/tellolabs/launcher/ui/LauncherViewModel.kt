package id.tellolabs.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.tellolabs.launcher.R
import id.tellolabs.launcher.data.apps.AppRepository
import id.tellolabs.launcher.data.preferences.LauncherPreferencesRepository
import id.tellolabs.launcher.model.LauncherApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LauncherViewModel internal constructor(
    application: Application,
    private val repository: LauncherPreferencesRepository,
    private val appRepository: AppRepository,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application, LauncherPreferencesRepository(application), AppRepository(application),
    )
    private val messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val errors = messages.asSharedFlow()
    private val mutableApps = MutableStateFlow<List<LauncherApp>?>(null)
    val apps = mutableApps.asStateFlow()
    private val mutableAppError = MutableStateFlow(false)
    val appError = mutableAppError.asStateFlow()
    private var refreshJob: Job? = null

    fun refreshApps() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val loaded = appRepository.getApps()
                mutableApps.value = loaded
                mutableAppError.value = false
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableAppError.value = true
            }
        }
    }

    fun setHidden(packageName: String, hidden: Boolean) = save { repository.setHidden(packageName, hidden) }
    fun switchShelf(direction: Int) = save { repository.switchShelf(direction) }
    fun setActiveShelf(id: String) = save { repository.setActiveShelf(id) }
    fun removeFromShelf(shelfId: String, packageName: String) = save { repository.removeFromShelf(shelfId, packageName) }

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
