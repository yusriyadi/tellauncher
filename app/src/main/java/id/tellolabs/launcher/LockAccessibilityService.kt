package id.tellolabs.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/** Provides only the system lock action; does not inspect screen content or events. */
class LockAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (connectedService === this) connectedService = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (connectedService === this) connectedService = null
        super.onDestroy()
    }

    companion object {
        private var connectedService: LockAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java)
            val component = ComponentName(context, LockAccessibilityService::class.java)
            return manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
            ).any {
                val info = it.resolveInfo.serviceInfo
                ComponentName(info.packageName, info.name) == component
            }
        }

        fun lockScreen(): Boolean = Build.VERSION.SDK_INT >= 28 &&
            connectedService?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}
