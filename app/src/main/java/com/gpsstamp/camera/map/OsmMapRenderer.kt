package com.gpsstamp.camera.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.gpsstamp.camera.geo.PLACE_MATCH_DISTANCE_M
import com.gpsstamp.camera.geo.haversineMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val ZOOM = 16
private const val SIZE_PX = 384
private const val USER_AGENT = "GPSStampCamera/1.0 (Android; com.gpsstamp.camera)"
private const val ATTRIBUTION = "© OpenStreetMap"

/** Builds a small square OpenStreetMap image centred on a point with a red pin; null if any tile fails. */
class OsmMapRenderer(context: Context) {
    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "osm_tiles"), 20L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val mutex = Mutex()
    private var cachedLat = 0.0
    private var cachedLon = 0.0
    private var cached: Bitmap? = null

    suspend fun render(lat: Double, lon: Double): Bitmap? = mutex.withLock {
        cached?.let { if (haversineMeters(cachedLat, cachedLon, lat, lon) <= PLACE_MATCH_DISTANCE_M) return it }
        val bitmap = withContext(Dispatchers.IO) {
            try {
                draw(lat, lon)
            } catch (e: IOException) {
                null
            }
        } ?: return null
        cachedLat = lat
        cachedLon = lon
        cached = bitmap
        bitmap
    }

    private fun draw(lat: Double, lon: Double): Bitmap {
        val center = OsmTileMath.worldPixel(lat, lon, ZOOM)
        val range = OsmTileMath.tileRange(center, SIZE_PX)
        val originX = center.x - SIZE_PX / 2.0
        val originY = center.y - SIZE_PX / 2.0
        val out = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        for (tx in range.minX..range.maxX) {
            for (ty in range.minY..range.maxY) {
                val tile = fetchTile(tx, ty)
                canvas.drawBitmap(
                    tile,
                    (tx * OsmTileMath.TILE_SIZE - originX).toFloat(),
                    (ty * OsmTileMath.TILE_SIZE - originY).toFloat(),
                    null
                )
                tile.recycle()
            }
        }
        drawPin(canvas, SIZE_PX / 2f, SIZE_PX / 2f)
        drawAttribution(canvas)
        return out
    }

    private fun fetchTile(x: Int, y: Int): Bitmap {
        val request = Request.Builder()
            .url("https://tile.openstreetmap.org/$ZOOM/$x/$y.png")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Tile $x/$y: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Tile $x/$y: empty body")
            return BitmapFactory.decodeStream(body.byteStream()) ?: throw IOException("Tile $x/$y: not an image")
        }
    }

    /** Teardrop pin, 36 px tall, with its tip on ([tipX], [tipY]). */
    private fun drawPin(canvas: Canvas, tipX: Float, tipY: Float) {
        val r = 12f
        val headY = tipY - 24f
        val head = RectF(tipX - r, headY - r, tipX + r, headY + r)
        val pin = Path().apply {
            moveTo(tipX, tipY)
            lineTo(tipX - r * 0.866f, headY + r * 0.5f)
            arcTo(head, 150f, 240f)
            close()
        }
        canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E53935") })
        canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        })
        canvas.drawCircle(tipX, headY, 4.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    private fun drawAttribution(canvas: Canvas) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = 13f
        }
        val width = text.measureText(ATTRIBUTION)
        val strip = Paint().apply { color = Color.argb(178, 255, 255, 255) }
        canvas.drawRect(SIZE_PX - width - 8f, SIZE_PX - 19f, SIZE_PX.toFloat(), SIZE_PX.toFloat(), strip)
        canvas.drawText(ATTRIBUTION, SIZE_PX - width - 4f, SIZE_PX - 5f, text)
    }
}
