package id.tellolabs.launcher

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val model: LauncherModel by viewModels()
    private val backupModel: SettingsBackupModel by viewModels()
    private var homeRequest by mutableIntStateOf(0)
    private var isDefaultHome by mutableStateOf(false)
    private var lockAccessibilityEnabled by mutableStateOf(false)
    private lateinit var appAuthenticator: AppAuthenticator
    private var authenticationSettingsUnlocked by mutableStateOf(false)
    private val defaultHomeRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateDefaultHome()
    }
    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) backupModel.discardExport()
        else lifecycleScope.launch { backupModel.writeExport(uri) }
    }
    private val importDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch { backupModel.readImport(uri) }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { model.refreshApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appAuthenticator = AppAuthenticator(this)
        homeRequest = savedInstanceState?.getInt("home_request") ?: 0
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this, packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }, ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.errors.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() } }
                launch { backupModel.events.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() } }
            }
        }
        setContent {
            val preferences by model.preferences.collectAsStateWithLifecycle()
            LauncherTheme(mode = preferences?.themeMode ?: ThemeMode.SYSTEM) {
                LauncherUi(
                    model = model,
                    backupModel = backupModel,
                    onExportSettings = ::requestExportSettings,
                    onImportSettings = ::requestImportSettings,
                    onConfirmImport = ::confirmSettingsImport,
                    homeRequest = homeRequest,
                    defaultHome = isDefaultHome,
                    onDefaultHome = ::requestDefaultHome,
                    onLaunch = ::requestAppLaunch,
                    authenticationSettingsUnlocked = authenticationSettingsUnlocked,
                    onAuthenticate = { action -> appAuthenticator.authenticate(action) },
                    onUnlockAuthenticationSettings = { action ->
                        appAuthenticator.authenticate {
                            authenticationSettingsUnlocked = true
                            action()
                        }
                    },
                    onLockAuthenticationSettings = { authenticationSettingsUnlocked = false },
                    onAppInfo = ::openAppInfo,
                    onOpenAlarm = {
                        openHomeShortcut(listOf(Intent(AlarmClock.ACTION_SHOW_ALARMS)), R.string.alarm_error)
                    },
                    onOpenCalendar = {
                        val today = CalendarContract.CONTENT_URI.buildUpon()
                            .appendPath("time").appendPath(System.currentTimeMillis().toString()).build()
                        openHomeShortcut(
                            listOf(Intent(Intent.ACTION_VIEW, today),
                                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)),
                            R.string.calendar_error,
                        )
                    },
                    lockAccessibilityEnabled = lockAccessibilityEnabled,
                    onAccessibilitySettings = ::openAccessibilitySettings,
                    onLockScreen = {
                        if (!LockAccessibilityService.lockScreen()) {
                            Toast.makeText(this, R.string.lock_failed, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refreshApps()
        lockAccessibilityEnabled = LockAccessibilityService.isEnabled(this)
        updateDefaultHome()
    }

    private fun updateDefaultHome() {
        isDefaultHome = if (Build.VERSION.SDK_INT >= 29) {
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
        } else {
            packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
            )?.activityInfo?.packageName == packageName
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        appAuthenticator.cancel()
        backupModel.discardImport()
        authenticationSettingsUnlocked = false
        setIntent(intent)
        homeRequest++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("home_request", homeRequest)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        appAuthenticator.cancel()
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    override fun onStop() {
        // Editing sessions end in the background. Legacy credential confirmation may stop us.
        authenticationSettingsUnlocked = false
        appAuthenticator.onHostStopped()
        super.onStop()
    }

    private fun requestAppLaunch(app: LauncherApp, onOpened: () -> Unit) {
        val preferences = model.preferences.value
        if (preferences == null) {
            Toast.makeText(this, R.string.preferences_error, Toast.LENGTH_SHORT).show()
            return
        }
        val open = { if (launchApp(app)) onOpened() }
        if (preferences.appAuthentication && app.packageName in preferences.protectedPackages) {
            appAuthenticator.authenticate(open)
        } else {
            open()
        }
    }

    private fun withSettingsAuthentication(action: () -> Unit) {
        val preferences = model.preferences.value
        if (preferences == null) {
            Toast.makeText(this, R.string.preferences_error, Toast.LENGTH_SHORT).show()
        } else if (preferences.appAuthentication) appAuthenticator.authenticate(action)
        else action()
    }

    private fun requestExportSettings() {
        if (backupModel.busy.value) return
        withSettingsAuthentication {
            lifecycleScope.launch {
                if (!backupModel.prepareExport()) return@launch
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    backupModel.discardExport()
                    return@launch
                }
                try {
                    exportDocument.launch("tellauncher-settings.json")
                } catch (_: RuntimeException) {
                    backupModel.discardExport()
                    Toast.makeText(this@MainActivity, R.string.backup_picker_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun requestImportSettings() {
        if (backupModel.busy.value) return
        try {
            importDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.backup_picker_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmSettingsImport() {
        if (backupModel.busy.value || backupModel.pendingImport.value == null) return
        // Check the CURRENT device's protection; never trust the flag in an imported file.
        withSettingsAuthentication {
            lifecycleScope.launch {
                if (backupModel.applyImport()) authenticationSettingsUnlocked = false
            }
        }
    }

    private fun launchApp(app: LauncherApp): Boolean = try {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = app.component
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        })
        true
    } catch (_: Exception) {
        Toast.makeText(this, R.string.launch_error, Toast.LENGTH_SHORT).show()
        model.refreshApps()
        false
    }

    private fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val role = getSystemService(RoleManager::class.java)
                if (role.isRoleAvailable(RoleManager.ROLE_HOME) && !role.isRoleHeld(RoleManager.ROLE_HOME)) {
                    defaultHomeRequest.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                }
            } catch (_: android.content.ActivityNotFoundException) {
                // Fall back to Home settings if the device cannot show the role dialog.
            } catch (_: SecurityException) {
                // Some devices restrict the role request activity.
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.default_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppInfo(app: LauncherApp) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", app.packageName, null)
            })
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_info_error, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.app_info_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openHomeShortcut(intents: List<Intent>, errorMessage: Int) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // Try the next supported system intent.
            } catch (_: SecurityException) {
                // Some device apps expose an intent but restrict access to it.
            }
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.accessibility_settings_error, Toast.LENGTH_LONG).show()
        }
    }
}
