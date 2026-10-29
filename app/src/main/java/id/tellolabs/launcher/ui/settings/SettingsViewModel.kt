package id.tellolabs.launcher.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.tellolabs.launcher.R
import id.tellolabs.launcher.data.preferences.LauncherPreferencesRepository
import id.tellolabs.launcher.data.preferences.Shelf
import id.tellolabs.launcher.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LauncherPreferencesRepository(application)
    private val messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val errors = messages.asSharedFlow()
    private val mutablePreferenceError = MutableStateFlow(false)
    val preferenceError = mutablePreferenceError.asStateFlow()
    val preferences = repository.preferences.catch {
        // Keep the drawer closed if saved hidden-app preferences cannot be read.
        mutablePreferenceError.value = true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setGrid(value: Boolean) = save { repository.setGrid(value) }
    fun setListIcons(value: Boolean) = save { repository.setListIcons(value) }
    fun setDoubleTapLock(value: Boolean) = save { repository.setDoubleTapLock(value) }
    fun setShowSeconds(value: Boolean) = save { repository.setShowSeconds(value) }
    fun setShowDrawer(value: Boolean) = save { repository.setShowDrawer(value) }
    fun setShowLauncherSettings(value: Boolean) = save { repository.setShowLauncherSettings(value) }
    fun setThemeMode(value: ThemeMode) = save { repository.setThemeMode(value) }
    fun setAppAuthentication(value: Boolean) = save { repository.setAppAuthentication(value) }
    fun setProtected(packageName: String, protected: Boolean) = save { repository.setProtected(packageName, protected) }
    fun setHidden(packageName: String, hidden: Boolean) = save { repository.setHidden(packageName, hidden) }
    fun setSwipeToSwitchShelves(enabled: Boolean) = save { repository.setSwipeToSwitchShelves(enabled) }
    suspend fun reorderShelves(ids: List<String>): Boolean = persist { repository.reorderShelves(ids) }
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
