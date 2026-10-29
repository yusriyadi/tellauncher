package id.tellolabs.launcher.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import id.tellolabs.launcher.data.apps.AppRepository
import id.tellolabs.launcher.data.preferences.LauncherPreferencesRepository
import id.tellolabs.launcher.model.LauncherApp
import id.tellolabs.launcher.testing.launcherApp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val appRepository = mockk<AppRepository>()
    private val store = ViewModelStore()
    private lateinit var model: LauncherViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        model = LauncherViewModel(mockk<Application>(), mockk<LauncherPreferencesRepository>(), appRepository)
        store.put("launcher", model)
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun pendingInitialLoadKeepsLoadingState() = runTest(dispatcher) {
        val result = CompletableDeferred<List<LauncherApp>>()
        coEvery { appRepository.getApps() } coAnswers { result.await() }
        assertNull(model.apps.value)
        model.refreshApps()
        runCurrent()
        assertNull(model.apps.value)
        assertFalse(model.appError.value)
        result.complete(listOf(launcherApp("com.example", "Example")))
        runCurrent()
        assertEquals(1, model.apps.value!!.size)
        assertFalse(model.appError.value)
    }

    @Test fun emptyResultFinishesLoadingSuccessfully() = runTest(dispatcher) {
        coEvery { appRepository.getApps() } returns emptyList()
        model.refreshApps()
        runCurrent()
        assertEquals(emptyList<LauncherApp>(), model.apps.value)
        assertFalse(model.appError.value)
    }

    @Test fun initialLoadFailureShowsErrorWithoutPublishingEmptyInventory() = runTest(dispatcher) {
        coEvery { appRepository.getApps() } throws IOException("Package query failed")
        model.refreshApps()
        runCurrent()
        assertNull(model.apps.value)
        assertTrue(model.appError.value)
    }

    @Test fun refreshKeepsPreviousInventoryUntilNewResultArrives() = runTest(dispatcher) {
        val previous = listOf(launcherApp("com.previous", "Previous"))
        coEvery { appRepository.getApps() } returns previous
        model.refreshApps()
        runCurrent()
        val result = CompletableDeferred<List<LauncherApp>>()
        coEvery { appRepository.getApps() } coAnswers { result.await() }
        model.refreshApps()
        runCurrent()
        assertEquals(previous, model.apps.value)
        val updated = listOf(launcherApp("com.updated", "Updated"))
        result.complete(updated)
        runCurrent()
        assertEquals(updated, model.apps.value)
    }

    @Test fun refreshFailureKeepsInventoryAndSuccessfulRetryClearsError() = runTest(dispatcher) {
        val previous = listOf(launcherApp("com.previous", "Previous"))
        coEvery { appRepository.getApps() } returns previous
        model.refreshApps()
        runCurrent()
        coEvery { appRepository.getApps() } throws IOException("Temporary failure")
        model.refreshApps()
        runCurrent()
        assertEquals(previous, model.apps.value)
        assertTrue(model.appError.value)
        val updated = listOf(launcherApp("com.updated", "Updated"))
        coEvery { appRepository.getApps() } returns updated
        model.refreshApps()
        runCurrent()
        assertEquals(updated, model.apps.value)
        assertFalse(model.appError.value)
    }

    @Test fun newerRefreshCancelsPreviousRequestAndCannotBeOverwrittenByIt() = runTest(dispatcher) {
        val stale = CompletableDeferred<List<LauncherApp>>()
        var cancelled = false
        coEvery { appRepository.getApps() } coAnswers {
            try { stale.await() } finally { cancelled = true }
        }
        model.refreshApps()
        runCurrent()
        val latest = listOf(launcherApp("com.latest", "Latest"))
        coEvery { appRepository.getApps() } returns latest
        model.refreshApps()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(latest, model.apps.value)
        assertFalse(model.appError.value)
        stale.complete(listOf(launcherApp("com.stale", "Stale")))
        runCurrent()
        assertEquals(latest, model.apps.value)
        coVerify(exactly = 2) { appRepository.getApps() }
    }

    @Test fun clearingViewModelCancelsPendingRefreshWithoutReportingFailure() = runTest(dispatcher) {
        val result = CompletableDeferred<List<LauncherApp>>()
        var cancelled = false
        coEvery { appRepository.getApps() } coAnswers {
            try { result.await() } finally { cancelled = true }
        }
        model.refreshApps()
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(cancelled)
        assertNull(model.apps.value)
        assertFalse(model.appError.value)
    }
}
