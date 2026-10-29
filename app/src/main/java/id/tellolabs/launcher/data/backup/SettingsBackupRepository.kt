package id.tellolabs.launcher.data.backup

import android.content.Context
import android.net.Uri
import id.tellolabs.launcher.data.preferences.LauncherPreferencesRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Owns backup snapshots, bounded JSON file I/O, validation, and restore policy. */
internal class SettingsBackupRepository(
    context: Context,
    private val settings: LauncherPreferencesRepository,
) {
    private val context = context.applicationContext

    suspend fun createExport(): String = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        SettingsBackupCodec.encode(settings.preferences.first(), version)
    }

    suspend fun writeExport(uri: Uri, json: String) = withContext(Dispatchers.IO) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size <= SettingsBackupCodec.MAX_BYTES)
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open backup")
        stream.use { it.write(bytes) }
    }

    suspend fun readImport(uri: Uri): SettingsBackup = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read backup")
        val bytes = stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > SettingsBackupCodec.MAX_BYTES) throw InvalidSettingsBackupException()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val json = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw InvalidSettingsBackupException()
        }
        SettingsBackupCodec.decode(json)
    }

    suspend fun restore(backup: SettingsBackup) {
        settings.replaceSettings(backup.settingsForRestore)
    }
}
