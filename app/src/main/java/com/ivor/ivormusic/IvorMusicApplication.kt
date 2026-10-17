package com.ivor.ivormusic

import android.app.Application
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ivor.ivormusic.data.CrashReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IvorMusicApplication : Application(), ImageLoaderFactory {

    /** Startup chores that nothing on the first frame waits for. */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else can crash, so the bug reporter has a
        // file to offer on the next launch. It wraps - never replaces - the
        // platform handler.
        CrashReporter.install(this)
        if (BuildConfig.DEBUG) enableStrictMode()
        // Wi-Fi sync with Triat desktop, on its own: at the foreground and after
        // local edits. Started here, once; it registers itself for the lifecycle.
        // Registration is cheap and has to happen before the first foreground.
        com.ivor.ivormusic.sync.AutoSync(this).start()
        // Reconciling the persisted periodic job with the user's current opt-in
        // reads preferences and writes to WorkManager's database, so it waits
        // off the main thread: nothing on the first frame depends on it, and
        // on a cold start that is disk work between the process and the window.
        startupScope.launch {
            com.ivor.ivormusic.work.ScheduledBackupWorker.sync(this@IvorMusicApplication)
        }
        // NewPipe's one-time setup, so the first YouTubeRepository a screen
        // builds finds it done instead of paying for it before the first frame.
        // Safe to race with a screen that builds one first: the init is
        // idempotent behind a lock.
        startupScope.launch {
            try {
                com.ivor.ivormusic.data.YouTubeRepository.preloadNewPipe(this@IvorMusicApplication)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.ivor.ivormusic.util.KLog.w("IvorMusicApplication", "NewPipe preload failed", e)
            }
        }
    }

    /**
     * Debug builds only: logs disk and network work on the main thread and
     * leaked closeables. Log only, never death - these are leads to chase,
     * and a crash here would hide the very screen being measured.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
    }

    /**
     * The one Coil loader for the whole process with 25% RAM memory cache,
     * 100MB disk cache and crossfade for smooth rendering.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                    .build()
            }
            .crossfade(true)
            .build()
}
