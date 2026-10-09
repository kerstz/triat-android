package com.ivor.ivormusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ivor.ivormusic.data.CrashReporter
import com.ivor.ivormusic.data.LocalVideoThumbnailFetcher
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
            .components {
                add(LocalVideoThumbnailFetcher.Factory(this@IvorMusicApplication))
                add(LocalVideoThumbnailFetcher.ThumbnailKeyer())
            }
            .crossfade(true)
            .build()
}
