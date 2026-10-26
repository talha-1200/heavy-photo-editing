package com.heavy.photoediting.engine

import android.graphics.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.*

/** All points are normalized (0..1) so they work at preview and full export resolution. */
data class FaceData(
    val box: RectF, val oval: List<PointF>, val leftEye: List<PointF>, val rightEye: List<PointF>,
    val brows: List<List<PointF>>, val lips: List<PointF>, val mouthOpen: List<PointF>
)

object FaceAnalyzer {
    /** On-device ML Kit (bundled model). Contours are only produced for the most prominent face. */
    suspend fun detect(bmp: Bitmap): FaceData? = suspendCoroutine { cont ->
        val det = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL).build()
        )
        det.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { faces ->
                val f = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                cont.resume(f?.let { toData(it, bmp.width.toFloat(), bmp.height.toFloat()) })
                det.close()
            }
            .addOnFailureListener { cont.resume(null); det.close() }
    }

    private fun pts(f: Face, type: Int, w: Float, h: Float) =
        f.getContour(type)?.points?.map { PointF(it.x / w, it.y / h) } ?: emptyList()

    private fun toData(f: Face, w: Float, h: Float): FaceData? {
        val oval = pts(f, FaceContour.FACE, w, h)
        if (oval.size < 8) return null
        val bb = f.boundingBox
        return FaceData(
            RectF(bb.left / w, bb.top / h, bb.right / w, bb.bottom / h), oval,
            pts(f, FaceContour.LEFT_EYE, w, h), pts(f, FaceContour.RIGHT_EYE, w, h),
            listOf(FaceContour.LEFT_EYEBROW_TOP, FaceContour.LEFT_EYEBROW_BOTTOM,
                FaceContour.RIGHT_EYEBROW_TOP, FaceContour.RIGHT_EYEBROW_BOTTOM).map { pts(f, it, w, h) },
            pts(f, FaceContour.UPPER_LIP_TOP, w, h) + pts(f, FaceContour.LOWER_LIP_BOTTOM, w, h).reversed(),
            pts(f, FaceContour.UPPER_LIP_BOTTOM, w, h) + pts(f, FaceContour.LOWER_LIP_TOP, w, h).reversed()
        )
    }
}

object Beauty {
    private fun path(p: List<PointF>, w: Int, h: Int) = Path().apply {
        p.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x * w, pt.y * h) else lineTo(pt.x * w, pt.y * h) }
        close()
    }

    /** Cheap Gaussian-like blur: repeated half-size downscale then staged upscale. */
    fun blur(src: Bitmap, radius: Float): Bitmap {
        val n = (ln(radius.coerceAtLeast(2f)) / ln(2f)).roundToInt().coerceIn(1, 5)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val sizes = ArrayList<Pair<Int, Int>>()
        var cur = src
        repeat(n) {
            sizes.add(cur.width to cur.height)
            val nb = Pipeline.newBmp(max(1, cur.width / 2), max(1, cur.height / 2))
            Canvas(nb).drawBitmap(cur, null, Rect(0, 0, nb.width, nb.height), paint)
            cur = nb
        }
        for (s in sizes.reversed()) {
            val nb = Pipeline.newBmp(s.first, s.second)
            Canvas(nb).drawBitmap(cur, null, Rect(0, 0, nb.width, nb.height), paint)
            cur = nb
        }
        return cur
    }

    private fun filtered(src: Bitmap, m: ColorMatrix): Bitmap {
        val out = Pipeline.newBmp(src.width, src.height)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
        return out
    }

    private val dstIn get() = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    private fun skinMask(w: Int, h: Int, f: FaceData, fw: Float): Bitmap {
        val m = Pipeline.newBmp(w, h); val c = Canvas(m)
        c.drawPath(path(f.oval, w, h), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; maskFilter = BlurMaskFilter((fw * 0.03f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        })
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); style = Paint.Style.FILL_AND_STROKE
            strokeWidth = fw * 0.025f; maskFilter = BlurMaskFilter((fw * 0.01f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        listOf(f.leftEye, f.rightEye, f.lips).plus(f.brows).filter { it.size >= 3 }.forEach { c.drawPath(path(it, w, h), cut) }
        return m
    }

    /** Skin smoothing, skin glow, teeth whitening - all masked to face contours. */
    fun skin(src: Bitmap, f: FaceData?, st: EditState): Bitmap {
        if (f == null || (st.smooth == 0 && st.glow == 0 && st.teeth == 0 && st.lip == 0 && st.blush == 0)) return src
        val w = src.width; val h = src.height
        val fw = f.box.width() * w
        val out = src.copy(Bitmap.Config.ARGB_8888, true); val c = Canvas(out)

        if (st.smooth > 0 || st.glow > 0) {
            val mask = skinMask(w, h, f, fw)
            if (st.smooth > 0) {
                val layer = blur(src, fw * 0.035f)
                Canvas(layer).drawBitmap(mask, 0f, 0f, dstIn)
                c.drawBitmap(layer, 0f, 0f, Paint().apply { alpha = (st.smooth * 2.4f).toInt() })
            }
            if (st.glow > 0) {
                val layer = filtered(out, ColorMatrix(floatArrayOf(
                    1.06f, 0f, 0f, 0f, 18f, 0f, 1.05f, 0f, 0f, 14f, 0f, 0f, 1.02f, 0f, 12f, 0f, 0f, 0f, 1f, 0f)))
                Canvas(layer).drawBitmap(mask, 0f, 0f, dstIn)
                c.drawBitmap(layer, 0f, 0f, Paint().apply { alpha = (st.glow * 2.2f).toInt() })
            }
        }
        if (st.teeth > 0 && f.mouthOpen.size >= 6) {
            val tm = Pipeline.newBmp(w, h)
            Canvas(tm).drawPath(path(f.mouthOpen, w, h), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; maskFilter = BlurMaskFilter((fw * 0.008f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            })
            val layer = filtered(out, ColorMatrix().apply { setSaturation(0.3f); postConcat(ColorMatrix(floatArrayOf(
                1.12f, 0f, 0f, 0f, 22f, 0f, 1.12f, 0f, 0f, 22f, 0f, 0f, 1.12f, 0f, 24f, 0f, 0f, 0f, 1f, 0f))) })
            Canvas(layer).drawBitmap(tm, 0f, 0f, dstIn)
            c.drawBitmap(layer, 0f, 0f, Paint().apply { alpha = (st.teeth * 2.4f).toInt() })
        }
        if (st.lip > 0 && f.lips.size >= 6) {                       // lipstick: lip polygon minus the open mouth, MULTIPLY blend
            val lm = Pipeline.newBmp(w, h); val mc = Canvas(lm)
            mc.drawPath(path(f.lips, w, h), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; maskFilter = BlurMaskFilter((fw * 0.006f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL) })
            if (f.mouthOpen.size >= 6) mc.drawPath(path(f.mouthOpen, w, h), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
            val layer = Pipeline.newBmp(w, h)
            Canvas(layer).apply { drawColor(0xFFC2185B.toInt()); drawBitmap(lm, 0f, 0f, dstIn) }
            c.drawBitmap(layer, 0f, 0f, Paint().apply { alpha = (st.lip * 1.8f).toInt(); blendMode = BlendMode.MULTIPLY })
        }
        if (st.blush > 0 && f.leftEye.size >= 4 && f.rightEye.size >= 4 && f.lips.size >= 6) {   // blush: soft radial pink on both cheeks
            fun ctr(p: List<PointF>) = PointF(p.map { it.x }.average().toFloat() * w, p.map { it.y }.average().toFloat() * h)
            val mo = ctr(f.lips); val r = fw * 0.2f
            val layer = Pipeline.newBmp(w, h); val lc = Canvas(layer)
            for (e in listOf(ctr(f.leftEye), ctr(f.rightEye))) {
                val cx = e.x + (mo.x - e.x) * 0.15f; val cy = e.y + (mo.y - e.y) * 0.55f
                lc.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx, cy, r, intArrayOf(0xCCFF6F91.toInt(), 0x00FF6F91), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP) })
            }
            c.drawBitmap(layer, 0f, 0f, Paint().apply { alpha = (st.blush * 2f).toInt().coerceAtMost(255) })
        }
        return out
    }

    /** Big eyes + face slimming via a bitmap mesh warp (experimental; see README). */
    fun warp(src: Bitmap, f: FaceData?, st: EditState): Bitmap {
        if (f == null || (st.eyes == 0 && st.slim == 0)) return src
        val w = src.width.toFloat(); val h = src.height.toFloat()
        val mw = 48; val mh = 48
        val v = FloatArray((mw + 1) * (mh + 1) * 2)

        class Eye(val cx: Float, val cy: Float, val r: Float)
        val eyes = listOf(f.leftEye, f.rightEye).filter { it.size >= 4 }.map { p ->
            val x0 = p.minOf { it.x } * w; val x1 = p.maxOf { it.x } * w
            val y0 = p.minOf { it.y } * h; val y1 = p.maxOf { it.y } * h
            Eye((x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) * 0.95f)
        }
        val ke = st.eyes / 100f * 0.45f
        val ks = st.slim / 100f * 0.14f
        val fx0 = f.oval.minOf { it.x } * w; val fx1 = f.oval.maxOf { it.x } * w
        val fy0 = f.oval.minOf { it.y } * h; val fy1 = f.oval.maxOf { it.y } * h
        val fcx = (fx0 + fx1) / 2; val half = (fx1 - fx0) / 2; val fh = fy1 - fy0

        var idx = 0
        for (j in 0..mh) for (i in 0..mw) {
            val x = w * i / mw; val y = h * j / mh
            var dx = x; var dy = y
            if (ke > 0f) for (e in eyes) {
                val ox = x - e.cx; val oy = y - e.cy; val d = hypot(ox, oy)
                if (d < e.r) {
                    val t = d / e.r; val s = ke * (1f - t * t)
                    dx += ox * s; dy += oy * s
                }
            }
            if (ks > 0f) {
                val ty = (y - (fy0 + 0.45f * fh)) / (0.7f * fh)
                val wy = if (ty in 0f..1f) sin(PI.toFloat() * ty) else 0f
                val ax = abs(x - fcx) / half
                val wx = when { ax <= 1f -> 1f; ax < 1.35f -> 1f - (ax - 1f) / 0.35f; else -> 0f }
                dx -= (x - fcx) * ks * wy * wx
            }
            v[idx++] = dx; v[idx++] = dy
        }
        val out = Pipeline.newBmp(src.width, src.height)
        Canvas(out).drawBitmapMesh(src, mw, mh, v, 0, null, 0, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
