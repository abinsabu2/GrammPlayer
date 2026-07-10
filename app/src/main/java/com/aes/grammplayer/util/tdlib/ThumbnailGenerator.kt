package com.aes.grammplayer.util.tdlib

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.aes.grammplayer.GPlayerApplication
import kotlin.random.Random

object ThumbnailGenerator {

    // Bump this when the generated look changes so old cached images are ignored
    // and regenerated in the new style instead of being reused.
    private const val STYLE_VERSION = "v2"

    private val colors = listOf(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4",
        "#F7B267", "#D4A5A5", "#9B59B6", "#3498DB"
    )

    /**
     * Generates a unique abstract background image: a diagonal two-colour gradient
     * with a handful of translucent geometric shapes scattered on top. There is no
     * text or glyph — it is purely a decorative background for the card slot.
     *
     * Everything is driven by a [seed]-derived RNG, so the same file always produces
     * the exact same image (stable across syncs) while different files look distinct.
     * The default dimensions use a portrait ratio (~0.86) matching the card's
     * thumbnail slot (148x172dp) so it fills the slot with centerCrop cleanly.
     */
    fun generatePlaceholder(seed: String, width: Int = 296, height: Int = 344): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Deterministic RNG: same seed -> same image every time.
        val rnd = Random(seed.hashCode().toLong())
        val w = width.toFloat()
        val h = height.toFloat()

        // Diagonal gradient background between two palette colours.
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w, h,
                pickColor(rnd), pickColor(rnd),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Scatter a few translucent shapes for depth and variety.
        val shapeCount = 4 + rnd.nextInt(4) // 4..7
        repeat(shapeCount) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pickColor(rnd)
                alpha = 45 + rnd.nextInt(85) // translucent 45..129
            }
            val cx = rnd.nextFloat() * w
            val cy = rnd.nextFloat() * h
            val size = w * 0.2f + rnd.nextFloat() * (w * 0.55f)

            when (rnd.nextInt(3)) {
                0 -> canvas.drawCircle(cx, cy, size / 2f, paint)
                1 -> canvas.drawRect(cx, cy, cx + size, cy + size * (0.6f + rnd.nextFloat()), paint)
                else -> {
                    val tri = Path().apply {
                        moveTo(cx, cy)
                        lineTo(cx + size, cy + size * 0.3f)
                        lineTo(cx - size * 0.2f, cy + size)
                        close()
                    }
                    canvas.drawPath(tri, paint)
                }
            }
        }

        // Subtle dark gradient at the bottom so overlaid card text stays readable.
        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, h * 0.55f, 0f, h,
                Color.TRANSPARENT, Color.parseColor("#66000000"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h * 0.55f, w, h, scrim)

        return bitmap
    }

    /** Picks a random colour from the palette using the given deterministic RNG. */
    private fun pickColor(rnd: Random): Int =
        Color.parseColor(colors[rnd.nextInt(colors.size)])

    /**
     * Deterministic solid palette colour for [seed]. Used to fill the thumbnail slot
     * instantly (edge-to-edge) before the full abstract image finishes generating.
     */
    fun colorFor(seed: String): Int =
        Color.parseColor(colors[(seed.hashCode() and 0x7fffffff) % colors.size])

    /**
     * Returns the absolute path of an already-generated placeholder for [key], or
     * null if none exists yet. Lets callers reuse a cached thumbnail instead of
     * regenerating (and re-randomising) it on every sync.
     */
    fun isGeneratedPlaceholder(path: String): Boolean =
        path.contains("/thumbnails/") && path.contains("_thumb_$STYLE_VERSION")

    fun existingThumbnail(key: String): String? {
        return try {
            val context = GPlayerApplication.Companion.AppContext
            val file = File(File(context.getExternalFilesDir(null), "thumbnails"), "${safeName(key)}_thumb_$STYLE_VERSION.jpg")
            if (file.exists()) file.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveBitmap(bitmap: Bitmap, fileName: String): String? {
        return try {
            val context = GPlayerApplication.Companion.AppContext
            val dir = File(context.getExternalFilesDir(null), "thumbnails")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "${safeName(fileName)}_thumb_$STYLE_VERSION.jpg")

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Strips characters that are unsafe in a file name (e.g. '/', ':' from titles). */
    private fun safeName(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "thumb" }
}