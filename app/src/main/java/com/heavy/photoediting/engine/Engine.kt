package com.heavy.photoediting.engine

import android.graphics.*
import kotlin.math.*

enum class CropAspect(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null), SQUARE("1:1", 1f), P45("4:5", 0.8f), P34("3:4", 0.75f),
    P916("9:16", 9f / 16f), L43("4:3", 4f / 3f), L169("16:9", 16f / 9f)
}

enum class FrameStyle(val label: String) {
    NONE("None"), WHITE("White"), BLACK("Black"), POLAROID("Polaroid"), ROUNDED("Rounded"), NEON("Neon"), FILM("Film")
}

/** Sticker (emoji) or text, positioned in normalized coordinates of the final image. */
data class Overlay(
    val id: Long, val text: String, val color: Int = Color.WHITE,
    val cx: Float = 0.5f, val cy: Float = 0.5f, val size: Float = 0.1f,
    val rot: Float = 0f, val isEmoji: Boolean = false
)

data class EditState(
    val brightness: Int = 0, val contrast: Int = 0, val saturation: Int = 0, val exposure: Int = 0,
    val rotation: Int = 0, val flipH: Boolean = false, val aspect: CropAspect = CropAspect.ORIGINAL,
    val filter: Int = 0, val filterStrength: Float = 1f,
    val smooth: Int = 0, val glow: Int = 0, val slim: Int = 0, val eyes: Int = 0, val teeth: Int = 0,
    val lip: Int = 0, val blush: Int = 0,
    val frame: FrameStyle = FrameStyle.NONE,
    val overlays: List<Overlay> = emptyList()
) {
    fun baseOnly() = copy(overlays = emptyList())
}

class FilterDef(
    val name: String, val sat: Float = 1f, val con: Float = 1f, val bri: Float = 0f,
    val r: Float = 1f, val g: Float = 1f, val b: Float = 1f, val fade: Float = 0f,
    val oR: Float = 0f, val oG: Float = 0f, val oB: Float = 0f, val vig: Float = 0f
) {
    fun matrix(): ColorMatrix {
        val m = ColorMatrix()
        m.setSaturation(sat)
        val k = 1f - fade
        val base = 128f * (1f - con) * k + bri * 255f + fade * 127f
        m.postConcat(ColorMatrix(floatArrayOf(
            con * r * k, 0f, 0f, 0f, base + oR,
            0f, con * g * k, 0f, 0f, base + oG,
            0f, 0f, con * b * k, 0f, base + oB,
            0f, 0f, 0f, 1f, 0f
        )))
        return m
    }
}

/** 33 original colour-grade recipes + "Original". All are plain colour-matrix maths: fully offline. */
object Filters {
    val all: List<FilterDef> = listOf(
        FilterDef("Original"),
        FilterDef("Aurora", sat = 1.15f, con = 1.05f, r = .98f, g = 1.03f, b = 1.08f),
        FilterDef("Ember", sat = 1.2f, con = 1.08f, r = 1.1f, b = .9f, bri = .01f),
        FilterDef("Glacier", sat = .95f, con = 1.1f, r = .92f, b = 1.12f),
        FilterDef("Dusk", sat = 1.1f, con = 1.1f, r = 1.08f, g = .95f, b = 1.05f, vig = .25f),
        FilterDef("Mint", sat = 1.05f, r = .95f, g = 1.07f, bri = .03f),
        FilterDef("Peach", sat = 1.1f, r = 1.08f, b = .95f, bri = .04f, fade = .03f),
        FilterDef("Velvet", sat = 1.1f, con = 1.2f, r = 1.04f, g = .94f, b = 1.02f, vig = .3f),
        FilterDef("Cloud", sat = .9f, con = .9f, bri = .05f, fade = .1f),
        FilterDef("Noir", sat = 0f, con = 1.25f, vig = .35f),
        FilterDef("Silver", sat = 0f, con = 1.05f, b = 1.03f, fade = .08f),
        FilterDef("Ink", sat = 0f, con = 1.5f, bri = -.03f, vig = .4f),
        FilterDef("Retro", sat = .8f, con = .95f, r = 1.1f, b = .82f, fade = .08f),
        FilterDef("Polaroid", sat = .9f, con = .95f, r = 1.05f, g = 1.02f, b = .92f, fade = .1f, oB = 6f),
        FilterDef("Sunny", sat = 1.3f, con = 1.05f, r = 1.08f, g = 1.04f, b = .88f, bri = .02f),
        FilterDef("Bloom", sat = 1.15f, r = 1.06f, g = .98f, b = 1.06f, bri = .04f, fade = .04f),
        FilterDef("Cinema", sat = .92f, con = 1.18f, r = 1.06f, b = .94f, oB = 4f, vig = .3f),
        FilterDef("Teal Orange", sat = 1.2f, con = 1.1f, r = 1.1f, g = .98f, b = .92f, oB = 5f),
        FilterDef("Matte", sat = .9f, con = .95f, fade = .14f),
        FilterDef("Chrome", sat = 1.2f, con = 1.25f),
        FilterDef("Pastel", sat = .85f, con = .9f, bri = .08f, fade = .06f),
        FilterDef("Rosewood", sat = 1.05f, r = 1.1f, g = .92f, b = .98f, vig = .2f),
        FilterDef("Forest", sat = 1.1f, r = .94f, g = 1.08f, b = .94f),
        FilterDef("Ocean", sat = 1.15f, r = .9f, b = 1.12f),
        FilterDef("Golden Hour", sat = 1.25f, r = 1.12f, g = 1.04f, b = .84f, bri = .02f, vig = .2f),
        FilterDef("Lavender", r = 1.03f, g = .95f, b = 1.1f, bri = .03f, fade = .05f),
        FilterDef("Night", sat = .9f, con = 1.15f, bri = -.06f, r = .9f, g = .98f, b = 1.15f, vig = .35f),
        FilterDef("Vivid", sat = 1.6f, con = 1.12f),
        FilterDef("Crisp", sat = 1.1f, con = 1.3f),
        FilterDef("Haze", sat = .85f, bri = .04f, r = 1.02f, b = 1.03f, fade = .18f),
        FilterDef("Amber", sat = 1.1f, r = 1.12f, b = .8f, fade = .05f),
        FilterDef("Lagoon", sat = 1.2f, r = .88f, g = 1.05f, b = 1.08f, bri = .02f),
        FilterDef("Blush", sat = 1.0f, r = 1.08f, g = .97f, b = 1.0f, bri = .05f, fade = .05f),
        FilterDef("Fade", sat = .75f, con = .9f, fade = .2f)
    )
}

object Pipeline {
    fun newBmp(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    /** beauty -> colour/filter -> geometry -> frame. Overlays are drawn separately (drawOverlays). */
    fun renderBase(src: Bitmap, face: FaceData?, st: EditState): Bitmap {
        var b = src
        b = Beauty.skin(b, face, st)
        b = Beauty.warp(b, face, st)
        b = color(b, st)
        b = geometry(b, st)
        b = frame(b, st.frame)
        return b
    }

    private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    fun colorMatrix(st: EditState): ColorMatrix? {
        if (st.brightness == 0 && st.contrast == 0 && st.saturation == 0 && st.exposure == 0 && st.filter == 0) return null
        val g = 2f.pow(st.exposure / 100f * 1.5f)
        val c = 1f + st.contrast / 100f * 0.7f
        val off = st.brightness / 100f * 60f + 128f * (1f - c)
        val m = ColorMatrix().apply { setSaturation((1f + st.saturation / 100f).coerceAtLeast(0f)) }
        m.postConcat(ColorMatrix(floatArrayOf(
            g * c, 0f, 0f, 0f, off, 0f, g * c, 0f, 0f, off, 0f, 0f, g * c, 0f, off, 0f, 0f, 0f, 1f, 0f)))
        if (st.filter > 0) {
            val fm = Filters.all[st.filter].matrix().array
            val s = st.filterStrength
            m.postConcat(ColorMatrix(FloatArray(20) { IDENTITY[it] + (fm[it] - IDENTITY[it]) * s }))
        }
        return m
    }

    fun color(src: Bitmap, st: EditState): Bitmap {
        val m = colorMatrix(st) ?: return src
        val out = newBmp(src.width, src.height)
        val c = Canvas(out)
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(m) })
        val vig = if (st.filter > 0) Filters.all[st.filter].vig * st.filterStrength else 0f
        if (vig > 0f) {
            val w = src.width.toFloat(); val h = src.height.toFloat()
            val p = Paint().apply {
                shader = RadialGradient(w / 2, h / 2, hypot(w, h) / 2,
                    intArrayOf(0, 0, ((vig * 255).toInt() shl 24)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, w, h, p)
        }
        return out
    }

    fun geometry(src: Bitmap, st: EditState): Bitmap {
        var b = src
        if (st.rotation != 0 || st.flipH) {
            val m = Matrix()
            if (st.flipH) m.postScale(-1f, 1f)
            m.postRotate(st.rotation * 90f)
            b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        }
        val r = st.aspect.ratio
        if (r != null) {
            var cw = b.width; var ch = b.height
            if (b.width.toFloat() / b.height > r) cw = (b.height * r).toInt() else ch = (b.width / r).toInt()
            cw = cw.coerceAtLeast(1); ch = ch.coerceAtLeast(1)
            b = Bitmap.createBitmap(b, (b.width - cw) / 2, (b.height - ch) / 2, cw, ch)
        }
        return b
    }

    fun frame(src: Bitmap, f: FrameStyle): Bitmap {
        if (f == FrameStyle.NONE) return src
        val w = src.width; val h = src.height
        val t = (min(w, h) * 0.04f).toInt().coerceAtLeast(4)
        return when (f) {
            FrameStyle.WHITE, FrameStyle.BLACK -> newBmp(w + 2 * t, h + 2 * t).also {
                val c = Canvas(it)
                c.drawColor(if (f == FrameStyle.WHITE) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
                c.drawBitmap(src, t.toFloat(), t.toFloat(), null)
            }
            FrameStyle.POLAROID -> newBmp(w + 2 * t, h + 5 * t).also {
                val c = Canvas(it); c.drawColor(0xFFF8F5EE.toInt()); c.drawBitmap(src, t.toFloat(), t.toFloat(), null)
            }
            FrameStyle.ROUNDED -> newBmp(w, h).also {
                val c = Canvas(it); c.drawColor(0xFFFFFFFF.toInt())
                val rad = min(w, h) * 0.07f
                c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), rad, rad, Path.Direction.CW) })
                c.drawBitmap(src, 0f, 0f, null)
            }
            FrameStyle.NEON -> src.copy(Bitmap.Config.ARGB_8888, true).also {
                val c = Canvas(it)
                val rect = RectF(t.toFloat(), t.toFloat(), (w - t).toFloat(), (h - t).toFloat())
                val shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFFFF4DA6.toInt(), 0xFF22D3EE.toInt(), Shader.TileMode.CLAMP)
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = t * 0.8f; this.shader = shader
                    maskFilter = BlurMaskFilter(t * 0.9f, BlurMaskFilter.Blur.NORMAL)
                }
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = t * 0.35f; this.shader = shader }
                c.drawRoundRect(rect, t * 1.5f, t * 1.5f, glow)
                c.drawRoundRect(rect, t * 1.5f, t * 1.5f, line)
            }
            FrameStyle.FILM -> {
                val pad = (h * 0.07f).toInt().coerceAtLeast(8)
                newBmp(w, h + 2 * pad).also {
                    val c = Canvas(it); c.drawColor(0xFF0B0B0B.toInt()); c.drawBitmap(src, 0f, pad.toFloat(), null)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEDEDED.toInt() }
                    val hw = pad * 0.5f
                    var x = hw * 0.6f
                    while (x < w) {
                        c.drawRoundRect(x, pad * 0.25f, x + hw, pad * 0.75f, 3f, 3f, p)
                        c.drawRoundRect(x, h + pad * 1.25f, x + hw, h + pad * 1.75f, 3f, 3f, p)
                        x += hw * 2.2f
                    }
                }
            }
            FrameStyle.NONE -> src
        }
    }

    /** Shared by the live preview and the exporter so both look identical. */
    fun drawOverlays(c: Canvas, w: Float, h: Float, list: List<Overlay>, selectedId: Long? = null) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFFFFFFFF.toInt()
            pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        }
        for (o in list) {
            p.textSize = o.size * w
            p.typeface = if (o.isEmoji) Typeface.DEFAULT else Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.color = o.color
            if (o.isEmoji) p.clearShadowLayer() else p.setShadowLayer(p.textSize * 0.06f, 0f, p.textSize * 0.03f, 0x99000000.toInt())
            val x = o.cx * w; val cy = o.cy * h
            c.save()
            c.rotate(o.rot, x, cy)
            val y = cy - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2f
            c.drawText(o.text, x, y, p)
            if (o.id == selectedId) {
                val hw = p.measureText(o.text) / 2f + 16f; val hh = p.textSize * 0.7f
                c.drawRoundRect(x - hw, cy - hh, x + hw, cy + hh, 14f, 14f, box)
            }
            c.restore()
        }
    }
}
