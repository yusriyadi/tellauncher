package id.tellolabs.launcher.ui.state

import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class ActivitySavedStateTest {
    private val schemaKey = "id.tellolabs.launcher.activity_state_schema"

    @Test fun coldStartHasNoStateToRestore() {
        assertNull(ActivitySavedState.compatibleOrNull(null))
    }

    @Test fun legacyStateWithoutMarkerIsDiscardedBeforeRestoration() {
        val oldState = mockk<Bundle>()
        every { oldState.getInt(schemaKey, 0) } returns 0
        assertNull(ActivitySavedState.compatibleOrNull(oldState))
        // Do not inspect nested legacy values: they may reference the removed enum class.
        verify(exactly = 1) { oldState.getInt(schemaKey, 0) }
    }

    @Test fun stateFromCurrentSchemaIsPreservedWithoutClearingItsContents() {
        val state = mockk<Bundle>()
        var schema = 0
        every { state.putInt(schemaKey, any()) } answers { schema = secondArg() }
        every { state.getInt(schemaKey, 0) } answers { schema }
        ActivitySavedState.mark(state)
        assertSame(state, ActivitySavedState.compatibleOrNull(state))
    }

    @Test fun unsupportedFutureSchemaIsDiscarded() {
        val futureState = mockk<Bundle>()
        every { futureState.getInt(schemaKey, 0) } returns 2
        assertNull(ActivitySavedState.compatibleOrNull(futureState))
    }

    @Test fun failedUnparcellingDoesNotReachActivityRestoration() {
        val oldState = mockk<Bundle>()
        every { oldState.getInt(schemaKey, 0) } throws RuntimeException(
            "Unable to read saved enum", ClassNotFoundException("id.tellolabs.launcher.Screen"),
        )
        assertNull(ActivitySavedState.compatibleOrNull(oldState))
    }
}
