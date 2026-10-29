package id.tellolabs.launcher

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsBackupModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsBackupRepository(application, LauncherPreferencesRepository(application))
    private val messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val events = messages.asSharedFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableImport = MutableStateFlow<SettingsBackup?>(null)
    val pendingImport = mutableImport.asStateFlow()
    // Immutable snapshot authorized before the system file picker. Never saved to disk/Bundle.
    private var pendingExport: String? = null

    suspend fun prepareExport(): Boolean = operation(R.string.backup_export_error) {
        pendingExport = null
        pendingExport = repository.createExport()
    }

    fun discardExport() { pendingExport = null }
    fun discardImport() { if (!mutableBusy.value) mutableImport.value = null }

    suspend fun writeExport(uri: Uri): Boolean = operation(R.string.backup_export_error) {
        val snapshot = pendingExport ?: error("No authorized export")
        pendingExport = null
        repository.writeExport(uri, snapshot)
        messages.emit(R.string.backup_export_success)
    }

    suspend fun readImport(uri: Uri): Boolean = operation(R.string.backup_read_error) {
        mutableImport.value = null
        mutableImport.value = repository.readImport(uri)
    }

    suspend fun applyImport(): Boolean = operation(R.string.backup_import_error) {
        val backup = mutableImport.value ?: error("No validated import")
        repository.restore(backup)
        mutableImport.value = null
        messages.emit(R.string.backup_import_success)
    }

    private suspend fun operation(errorMessage: Int, action: suspend () -> Unit): Boolean {
        if (mutableBusy.value) return false
        mutableBusy.value = true
        return try {
            action()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: UnsupportedSettingsBackupException) {
            messages.emit(R.string.backup_unsupported)
            false
        } catch (_: InvalidSettingsBackupException) {
            messages.emit(R.string.backup_invalid)
            false
        } catch (_: Exception) {
            messages.emit(errorMessage)
            false
        } finally {
            mutableBusy.value = false
        }
    }
}
