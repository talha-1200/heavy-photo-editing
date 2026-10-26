package com.heavy.photoediting.engine

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Offline person cut-out: ML Kit Selfie Segmentation (on-device model) + our own post-processing.
 *
 * Tuning cheat-sheet (CutoutParams):
 *  - low / high   : confidence ramp. Lower `low` (0.2) keeps more hair/clothes/hands (safer for "never cut the subject").
 *                   Raise `high` (0.8) if background halos remain.
 *  - growPx       : dilation of the mask. +2..+4 protects fine hair and fingertips; negative values shrink (remove halo).
 *  - keepBlobs    : drops floating fragments, keeps the main body (and blobs of >= 8% of its size, e.g. a separate hand).
 *  - guided*      : guided-filter matting. Snaps the mask to real image edges and recovers hair strands.
 *  - featherPx    : final 1-2 px softening so edges are never jagged or boxy.
 */
data class CutoutParams(
    val low: Float = 0.25f,
    val high: Float = 0.70f,
    val growPx: Int = 2,
    val featherPx: Int = 1,
    val keepBlobs: Boolean = true,
    val guidedRadius: Int = 8,
    val guidedEps: Float = 1e-3f
)

object PersonCutout {
    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build() // mask size == input image size (no raw-size flag)
        )
    }

    /** Raw model confidence 0..1 per pixel (1 = person), row-major, size = bmp.width * bmp.height. */
    suspend fun confidence(bmp: Bitmap): FloatArray = suspendCoroutine { cont ->
        segmenter.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { m ->
                val n = m.width * m.height
                val out = FloatArray(n)
                val buf = m.buffer
                buf.rewind()
                for (i in 0 until n) out[i] = buf.float
                cont.resume(out)
            }
            .addOnFailureListener { cont.resume(FloatArray(bmp.width * bmp.height)) }
    }

    /** Full pipeline: returns ARGB bitmap where only the person is opaque. */
    suspend fun cutout(src: Bitmap, p: CutoutParams = CutoutParams()): Bitmap {
        val w = src.width; val h = src.height
        val conf = confidence(src)
        val alpha = refine(conf, src, w, h, p)
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) px[i] = ((alpha[i] * 255f + .5f).toInt().coerceIn(0, 255) shl 24) or (px[i] and 0xFFFFFF)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun refine(conf: FloatArray, src: Bitmap, w: Int, h: Int, p: CutoutParams): FloatArray {
        // 1) soft threshold ramp (smoothstep between low and high)
        var a = FloatArray(conf.size) { val t = ((conf[it] - p.low) / (p.high - p.low)).coerceIn(0f, 1f); t * t * (3 - 2 * t) }
        // 2) morphological grow / shrink (protects hair, fingers)
        if (p.growPx > 0) a = morph(a, w, h, p.growPx, true) else if (p.growPx < 0) a = morph(a, w, h, -p.growPx, false)
        // 3) drop floating fragments
        if (p.keepBlobs) a = keepMain(a, w, h)
        // 4) guided-filter matting against the image itself (recovers hair strands, snaps to edges)
        val gray = FloatArray(w * h)
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) { val c = px[i]; gray[i] = (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f }
        a = guided(gray, a, w, h, p.guidedRadius, p.guidedEps)
        // 5) contrast the matte a little, then feather
        for (i in a.indices) { val t = ((a[i] - 0.3f) / 0.4f).coerceIn(0f, 1f); a[i] = t * t * (3 - 2 * t) }
        if (p.featherPx > 0) a = box(a, w, h, p.featherPx)
        return a
    }

    private fun morph(a: FloatArray, w: Int, h: Int, r: Int, grow: Boolean): FloatArray {
        fun pass(src: FloatArray, horizontal: Boolean): FloatArray {
            val out = FloatArray(src.size)
            for (y in 0 until h) for (x in 0 until w) {
                var v = if (grow) 0f else 1f
                for (d in -r..r) {
                    val xx = if (horizontal) (x + d).coerceIn(0, w - 1) else x
                    val yy = if (horizontal) y else (y + d).coerceIn(0, h - 1)
                    val s = src[yy * w + xx]
                    v = if (grow) max(v, s) else min(v, s)
                }
                out[y * w + x] = v
            }
            return out
        }
        return pass(pass(a, true), false)
    }

    /** Keeps the largest blob plus any blob >= 8% of it; removes the rest (floating fragments). */
    private fun keepMain(a: FloatArray, w: Int, h: Int): FloatArray {
        val n = w * h
        val lab = IntArray(n); val sizes = ArrayList<Int>(); sizes.add(0)
        val stack = IntArray(n)
        var L = 0
        for (s in 0 until n) {
            if (a[s] <= 0.5f || lab[s] != 0) continue
            L++; var sp = 0; var c = 0; stack[sp++] = s; lab[s] = L
            while (sp > 0) {
                val p = stack[--sp]; c++
                val x = p % w; val y = p / w
                if (x > 0 && a[p - 1] > .5f && lab[p - 1] == 0) { lab[p - 1] = L; stack[sp++] = p - 1 }
                if (x < w - 1 && a[p + 1] > .5f && lab[p + 1] == 0) { lab[p + 1] = L; stack[sp++] = p + 1 }
                if (y > 0 && a[p - w] > .5f && lab[p - w] == 0) { lab[p - w] = L; stack[sp++] = p - w }
                if (y < h - 1 && a[p + w] > .5f && lab[p + w] == 0) { lab[p + w] = L; stack[sp++] = p + w }
            }
            sizes.add(c)
        }
        val biggest = sizes.maxOrNull() ?: 0
        val out = a.copyOf()
        for (i in 0 until n) {
            val l = lab[i]
            if (l != 0 && sizes[l] < biggest * 0.08f) out[i] = 0f   // floating fragment
        }
        // soft pixels (<=0.5) that belong to no blob stay as they are: they are the hair/edge transition
        return out
    }

    private fun box(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        // integral-image box filter with edge clamping
        val W = w + 1
        val ii = DoubleArray(W * (h + 1))
        for (y in 0 until h) { var row = 0.0; for (x in 0 until w) { row += src[y * w + x]; ii[(y + 1) * W + x + 1] = ii[y * W + x + 1] + row } }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - r); val y1 = min(h - 1, y + r)
            for (x in 0 until w) {
                val x0 = max(0, x - r); val x1 = min(w - 1, x + r)
                val s = ii[(y1 + 1) * W + x1 + 1] - ii[y0 * W + x1 + 1] - ii[(y1 + 1) * W + x0] + ii[y0 * W + x0]
                out[y * w + x] = (s / ((x1 - x0 + 1) * (y1 - y0 + 1))).toFloat()
            }
        }
        return out
    }

    /** He et al. guided filter (gray guide). */
    private fun guided(I: FloatArray, p: FloatArray, w: Int, h: Int, r: Int, eps: Float): FloatArray {
        val n = w * h
        val Ip = FloatArray(n) { I[it] * p[it] }
        val II = FloatArray(n) { I[it] * I[it] }
        val mI = box(I, w, h, r); val mp = box(p, w, h, r); val mIp = box(Ip, w, h, r); val mII = box(II, w, h, r)
        val a = FloatArray(n); val b = FloatArray(n)
        for (i in 0 until n) { a[i] = (mIp[i] - mI[i] * mp[i]) / (mII[i] - mI[i] * mI[i] + eps); b[i] = mp[i] - a[i] * mI[i] }
        val ma = box(a, w, h, r); val mb = box(b, w, h, r)
        return FloatArray(n) { (ma[it] * I[it] + mb[it]).coerceIn(0f, 1f) }
    }
}
