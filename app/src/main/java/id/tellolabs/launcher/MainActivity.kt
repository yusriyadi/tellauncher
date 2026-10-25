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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: LauncherModel by viewModels()
    private var homeRequest by mutableIntStateOf(0)
    private var isDefaultHome by mutableStateOf(false)
    private var lockAccessibilityEnabled by mutableStateOf(false)
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { model.refreshApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeRequest = savedInstanceState?.getInt("home_request") ?: 0
        enableEdgeToEdge()
        showSystemBars()
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
                model.errors.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            }
        }
        setContent {
            LauncherTheme {
                LauncherUi(
                    model = model,
                    homeRequest = homeRequest,
                    defaultHome = isDefaultHome,
                    onDefaultHome = ::requestDefaultHome,
                    onLaunch = ::launchApp,
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
        setIntent(intent)
        homeRequest++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("home_request", homeRequest)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun showSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            show(WindowInsetsCompat.Type.systemBars())
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
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val role = getSystemService(RoleManager::class.java)
                if (role.isRoleAvailable(RoleManager.ROLE_HOME) && !role.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivity(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                }
            }
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
