package pl.misticklite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.max
import kotlin.math.min

data class Artwork(val bitmap: Bitmap, val banner: Boolean)

/** Only visible tiles request artwork; decoded images have a hard 3 MiB cache budget. */
class ArtworkLoader(private val context: Context, private val changed: (String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val pending = HashSet<String>()
    private val failed = HashSet<String>()
    private val versions = HashMap<String, Int>()
    @Volatile private var closed = false
    private val cache = object : LruCache<String, Artwork>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Artwork) = value.bitmap.allocationByteCount
    }

    fun get(pkg: String): Artwork? = cache.get(pkg)

    fun request(app: TvApp) {
        val pkg = app.key.packageName
        if (closed || cache.get(pkg) != null || pkg in failed || !pending.add(pkg)) return
        val version = versions[pkg] ?: 0
        try {
            worker.execute {
                val artwork = try { decode(app) } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
                handler.post {
                    pending.remove(pkg)
                    if (!closed && version == (versions[pkg] ?: 0)) {
                        if (artwork != null) cache.put(pkg, artwork) else failed.add(pkg)
                        changed(pkg)
                    }
                }
            }
        } catch (_: RejectedExecutionException) { pending.remove(pkg) }
    }

    fun invalidatePackage(pkg: String) {
        versions[pkg] = (versions[pkg] ?: 0) + 1
        cache.remove(pkg)
        failed.remove(pkg)
    }
    fun trim() { cache.evictAll() }
    fun close() {
        closed = true
        worker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        cache.evictAll()
    }

    private fun decode(app: TvApp): Artwork? {
        val info = app.activityInfo
        val res = context.packageManager.getResourcesForApplication(info.applicationInfo)
        val bannerId = info.banner.takeIf { it != 0 } ?: info.applicationInfo.banner
        val isBanner = bannerId != 0
        val id = if (isBanner) bannerId else info.icon.takeIf { it != 0 } ?: info.applicationInfo.icon
        if (id == 0) return null
        val maxW = if (isBanner) 384 else 128
        val maxH = if (isBanner) 216 else 128
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, id, bounds)
        val bitmap: Bitmap
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            var sample = 1
            while (bounds.outWidth / sample > maxW * 2 || bounds.outHeight / sample > maxH * 2) sample *= 2
            val decoded = BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply {
                inSampleSize = sample
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return null
            val scale = min(1f, min(maxW.toFloat() / decoded.width, maxH.toFloat() / decoded.height))
            bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded,
                max(1, (decoded.width * scale).toInt()), max(1, (decoded.height * scale).toInt()), true) else decoded
            if (bitmap !== decoded) decoded.recycle()
        } else {
            val drawable = res.getDrawable(id, null)
            val w = max(1, drawable.intrinsicWidth)
            val h = max(1, drawable.intrinsicHeight)
            val scale = min(maxW.toFloat() / w, maxH.toFloat() / h)
            bitmap = Bitmap.createBitmap(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()), Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
        }
        return Artwork(bitmap, isBanner)
    }
}
