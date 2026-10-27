package id.tellolabs.launcher

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle

/** System authentication only. Success and pending actions are never persisted or restored. */
internal class AppAuthenticator(private val activity: FragmentActivity) {
    private var requestId = 0L
    private var prompt: BiometricPrompt? = null
    private val keyguard = activity.getSystemService(KeyguardManager::class.java)
    private var credentialInProgress = false
    private var credentialAction: (() -> Unit)? = null
    private val credentialRequest = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val action = credentialAction
        credentialAction = null
        credentialInProgress = false
        if (result.resultCode == Activity.RESULT_OK && action != null && canComplete()) action()
    }

    fun authenticate(onSuccess: () -> Unit) {
        if (prompt != null || credentialInProgress || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val modern = Build.VERSION.SDK_INT >= 30
        val authenticators = if (modern) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else BiometricManager.Authenticators.BIOMETRIC_STRONG
        val status = BiometricManager.from(activity).canAuthenticate(authenticators)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            if (!modern && keyguard.isDeviceSecure) {
                confirmDeviceCredential(onSuccess)
            } else {
                Toast.makeText(activity,
                    if (!keyguard.isDeviceSecure) R.string.screen_lock_required else R.string.biometric_unavailable,
                    Toast.LENGTH_LONG).show()
            }
            return
        }
        val id = ++requestId
        val request = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (id != requestId || prompt == null) return
                    prompt = null
                    requestId++
                    if (canComplete()) onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (id != requestId || prompt == null) return
                    prompt = null
                    requestId++
                    if (!modern && keyguard.isDeviceSecure &&
                        (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                            errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT ||
                            errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                            errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                            errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT)) {
                        confirmDeviceCredential(onSuccess)
                        return
                    }
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        Toast.makeText(activity, R.string.biometric_failed, Toast.LENGTH_LONG).show()
                    }
                }
                // A non-matching biometric stays in the system prompt for another attempt.
            })
        prompt = request
        try {
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_title))
                .setSubtitle(activity.getString(R.string.biometric_subtitle))
                .setAllowedAuthenticators(authenticators)
            if (!modern) {
                info.setNegativeButtonText(activity.getString(
                    if (keyguard.isDeviceSecure) R.string.use_screen_lock else R.string.cancel))
            }
            request.authenticate(info.build())
        } catch (_: RuntimeException) {
            cancel()
            Toast.makeText(activity, R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmDeviceCredential(onSuccess: () -> Unit) {
        // Android 8–10 cannot use STRONG | DEVICE_CREDENTIAL in BiometricPrompt.
        if (credentialInProgress || !keyguard.isDeviceSecure || !canComplete()) return
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            activity.getString(R.string.biometric_title), activity.getString(R.string.biometric_subtitle))
        if (intent == null) {
            Toast.makeText(activity, R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        credentialAction = onSuccess
        credentialInProgress = true
        try {
            credentialRequest.launch(intent)
        } catch (_: RuntimeException) {
            credentialAction = null
            credentialInProgress = false
            Toast.makeText(activity, R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun canComplete(): Boolean = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
        !activity.isFinishing && !activity.isDestroyed

    fun onHostStopped() {
        // The explicitly launched legacy system credential screen stops its caller normally.
        if (!credentialInProgress) cancel()
    }

    fun cancel() {
        requestId++
        credentialAction = null
        val active = prompt
        prompt = null
        active?.cancelAuthentication()
    }
}
