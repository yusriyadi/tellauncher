package id.tellolabs.launcher.ui.state

import android.os.Bundle

/** Validates transient Activity state before FragmentActivity attempts restoration. */
internal object ActivitySavedState {
    private const val SCHEMA_KEY = "id.tellolabs.launcher.activity_state_schema"
    private const val SCHEMA_VERSION = 1

    fun compatibleOrNull(state: Bundle?): Bundle? {
        if (state == null) return null
        return try {
            // Pre-refactor Bundles have no marker and can contain the removed enum class.
            // Only transient UI state is discarded; DataStore is independent of this Bundle.
            state.takeIf { it.getInt(SCHEMA_KEY, 0) == SCHEMA_VERSION }
        } catch (_: RuntimeException) {
            // Reading even the marker can trigger unparcelling of an unavailable old class.
            null
        }
    }

    fun mark(state: Bundle) {
        state.putInt(SCHEMA_KEY, SCHEMA_VERSION)
    }
}
