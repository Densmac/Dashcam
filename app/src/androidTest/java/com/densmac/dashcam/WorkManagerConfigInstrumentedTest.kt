package com.densmac.dashcam

import androidx.hilt.work.HiltWorkerFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the download pipeline against a regression where WorkManager falls back to its
 * default reflection-based WorkerFactory and cannot construct the @HiltWorker
 * [com.densmac.dashcam.data.download.DownloadWorker] (NoSuchMethodException on the
 * (Context, WorkerParameters) constructor), so downloads never start.
 *
 * This happens when the default androidx.startup WorkManagerInitializer is left enabled in
 * the manifest, so it initializes WorkManager before DashcamApp's Configuration.Provider.
 * The fix removes that initializer; this test verifies the app still provides the Hilt
 * factory on a real device.
 */
@RunWith(AndroidJUnit4::class)
class WorkManagerConfigInstrumentedTest {

    @Test
    fun applicationProvidesHiltWorkerFactory() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext

        assertTrue(
            "DashcamApp must implement Configuration.Provider so WorkManager uses the Hilt factory",
            application is Configuration.Provider
        )

        val factory = (application as Configuration.Provider).workManagerConfiguration.workerFactory
        assertTrue(
            "Expected HiltWorkerFactory but was ${factory::class.java.name}; " +
                "WorkManager would fall back to reflection and fail to create @HiltWorker workers",
            factory is HiltWorkerFactory
        )
    }
}
