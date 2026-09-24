package com.ivor.ivormusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ivor.ivormusic.data.CrashReporter
import com.ivor.ivormusic.data.LocalVideoThumbnail
import com.ivor.ivormusic.data.LocalVideoThumbnailFetcher

class IvorMusicApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else can crash, so the bug reporter has a
        // file to offer on the next launch. It wraps - never replaces - the
        // platform handler.
        CrashReporter.install(this)
        // Reconcile the persisted periodic job with the user's current opt-in.
        // The setting handler does the same immediately when the value changes.
        com.ivor.ivormusic.work.UploadCheckWorker.sync(this)
        com.ivor.ivormusic.work.ScheduledBackupWorker.sync(this)
        // Wi-Fi sync with Triat desktop, on its own: at the foreground and after
        // local edits. Started here, once; it registers itself for the lifecycle.
        com.ivor.ivormusic.sync.AutoSync(this).start()
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
