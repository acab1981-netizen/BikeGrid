package com.example.bike_.data.map

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import java.io.File
import kotlin.math.max

object AppMapConfig {

    fun ensureConfigured(context: Context) {
        val appCtx = context.applicationContext
        val base = baseDir(appCtx).apply { mkdirs() }
        val tiles = tilesDir(appCtx).apply { mkdirs() }
        val ua = userAgent(appCtx)
        val cfg = Configuration.getInstance()
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cfg.load(appCtx, prefs)
        cfg.userAgentValue = ua
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = tiles
        // keep debug low in production; can be toggled later if needed
        cfg.isDebugMode = false
        cfg.isDebugTileProviders = false
        cfg.isDebugMapTileDownloader = false
        try {
            cfg.cacheMapTileCount = 12
            // larger on-disk cache limit for offline map usage
            cfg.tileFileSystemCacheMaxBytes = 512L * 1024L * 1024L
            cfg.tileFileSystemCacheTrimBytes = 460L * 1024L * 1024L
            cfg.tileDownloadThreads = 4
            cfg.tileDownloadMaxQueueSize = 40
            // keep cached tiles for a week by default; offline downloads are user-managed
            cfg.expirationOverrideDuration = 7L * 24L * 60L * 60L * 1000L
        } catch (_: Throwable) {
        }
    }

    fun baseDir(context: Context): File = File(context.applicationContext.filesDir, "osmdroid")

    fun tilesDir(context: Context): File = File(baseDir(context), "tiles")

    fun cacheDbFile(context: Context): File = File(baseDir(context), "cache.db")

    fun calculateCacheSizeBytes(context: Context): Long {
        val appCtx = context.applicationContext
        var total = 0L
        total += dirSize(baseDir(appCtx))
        // include legacy cacheDir location too, in case older data remains
        total += dirSize(File(appCtx.cacheDir, "osmdroid"))
        return total
    }

    fun clearCache(context: Context): Boolean {
        val appCtx = context.applicationContext
        var ok = true
        if (baseDir(appCtx).exists()) ok = baseDir(appCtx).deleteRecursively() && ok
        if (File(appCtx.cacheDir, "osmdroid").exists()) ok = File(appCtx.cacheDir, "osmdroid").deleteRecursively() && ok
        // recreate expected dirs
        baseDir(appCtx).mkdirs()
        tilesDir(appCtx).mkdirs()
        try {
            val cfg = Configuration.getInstance()
            cfg.osmdroidBasePath = baseDir(appCtx)
            cfg.osmdroidTileCache = tilesDir(appCtx)
        } catch (_: Throwable) { }
        return ok
    }

    private fun dirSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            size += if (child.isDirectory) dirSize(child) else child.length()
        }
        return size
    }

    private fun userAgent(context: Context): String {
        val v = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) { "1.0" }
        // OSM tile servers require a clear, app-specific User-Agent (not the library default).
        // Keep it descriptive and stable to comply with tile usage policies.
        return "Bike_/v$v (${context.packageName}; Android ${android.os.Build.VERSION.RELEASE}; osmdroid)"
    }

    const val PREFS_NAME = "osmdroid"
}

object MapTileSources {
    const val MAPNIK = "MAPNIK" // kept for settings compatibility, mapped to a policy-friendly OSM style
    const val OSM_FR = "OSM_FR"
    const val OPENTOPO = "OPENTOPO"
    const val OSM_HOT = "OSM_HOT"
    const val CYCLOSM = "CYCLOSM"

    val allKeys = listOf(CYCLOSM, OSM_FR, OPENTOPO, OSM_HOT)

    fun label(key: String): String = when (key) {
        MAPNIK, OSM_FR -> "OSM France"
        OPENTOPO -> "OpenTopoMap"
        OSM_HOT -> "OSM Humanitarian"
        CYCLOSM -> "CyclOSM"
        else -> key
    }

    fun source(key: String): ITileSource = when (key) {
        MAPNIK, OSM_FR -> XYTileSource(
            "OSMFrance", 0, 19, 256, ".png",
            arrayOf(
                "https://a.tile.openstreetmap.fr/osmfr/",
                "https://b.tile.openstreetmap.fr/osmfr/",
                "https://c.tile.openstreetmap.fr/osmfr/"
            ),
            "© OpenStreetMap contributors, tiles by OpenStreetMap France"
        )
        OPENTOPO -> XYTileSource(
            "OpenTopoMap", 0, 17, 256, ".png",
            arrayOf(
                "https://a.tile.opentopomap.org/",
                "https://b.tile.opentopomap.org/",
                "https://c.tile.opentopomap.org/"
            ),
            "© OpenStreetMap contributors, SRTM | © OpenTopoMap"
        )
        OSM_HOT -> XYTileSource(
            "OSMHumanitarian", 0, 19, 256, ".png",
            arrayOf(
                "https://a.tile.openstreetmap.fr/hot/",
                "https://b.tile.openstreetmap.fr/hot/"
            ),
            "© OpenStreetMap contributors, HOT"
        )
        CYCLOSM -> XYTileSource(
            "CyclOSM", 0, 19, 256, ".png",
            arrayOf(
                "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/",
                "https://b.tile-cyclosm.openstreetmap.fr/cyclosm/",
                "https://c.tile-cyclosm.openstreetmap.fr/cyclosm/"
            ),
            "© OpenStreetMap contributors, CyclOSM"
        )
        else -> source(CYCLOSM)
    }

    fun maxZoomForDownload(key: String, requestedMax: Int): Int {
        val srcMax = source(key).maximumZoomLevel
        return max(0, minOf(requestedMax, srcMax))
    }
}
