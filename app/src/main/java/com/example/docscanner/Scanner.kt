package com.example.docscanner

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max

enum class ScanFilter(val label: String) {
    MAGIC("Magic"),
    BW("B&W"),
    GRAY("Gray"),
    ORIGINAL("Original")
}

object Scanner {
    private var loaded = false

    fun init() {
        if (!loaded) loaded = OpenCVLoader.initLocal()
    }

    /** Finds the document quad. Returns corners ordered TL, TR, BR, BL in bitmap pixels. */
    fun detectCorners(src: Bitmap): List<PointF> {
        val full = Mat()
        Utils.bitmapToMat(src, full)

        val scale = (600.0 / max(full.rows(), full.cols())).coerceAtMost(1.0)
        val small = Mat()
        Imgproc.resize(full, small, Size(full.cols() * scale, full.rows() * scale))

        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val minArea = 0.15 * small.rows() * small.cols()
        var best: List<Point>? = null
        var bestArea = 0.0
        for (c in contours) {
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            if (approx.total() != 4L) continue
            val quad = MatOfPoint(*approx.toArray())
            if (!Imgproc.isContourConvex(quad)) continue
            val area = Imgproc.contourArea(quad)
            if (area > minArea && area > bestArea) {
                bestArea = area
                best = approx.toList()
            }
        }

        val pts = best?.map { PointF((it.x / scale).toFloat(), (it.y / scale).toFloat()) }
            ?: run {
                val mx = src.width * 0.06f
                val my = src.height * 0.06f
                listOf(
                    PointF(mx, my), PointF(src.width - mx, my),
                    PointF(src.width - mx, src.height - my), PointF(mx, src.height - my)
                )
            }
        return order(pts)
    }

    private fun order(p: List<PointF>): List<PointF> {
        val tl = p.minBy { it.x + it.y }
        val br = p.maxBy { it.x + it.y }
        val tr = p.minBy { it.y - it.x }
        val bl = p.maxBy { it.y - it.x }
        return listOf(tl, tr, br, bl)
    }

    private fun dist(a: PointF, b: PointF) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    /** Perspective-corrects the quad (TL, TR, BR, BL) into a flat, upright rectangle. */
    fun warp(src: Bitmap, c: List<PointF>): Bitmap {
        val (tl, tr, br, bl) = order(c)
        var w = max(dist(tl, tr), dist(bl, br))
        var h = max(dist(tl, bl), dist(tr, br))
        val limit = 3000.0
        val k = limit / max(w, h)
        if (k < 1.0) { w *= k; h *= k }
        val ow = w.toInt().coerceAtLeast(1)
        val oh = h.toInt().coerceAtLeast(1)

        val srcMat = Mat()
        Utils.bitmapToMat(src, srcMat)
        val from = MatOfPoint2f(
            Point(tl.x.toDouble(), tl.y.toDouble()), Point(tr.x.toDouble(), tr.y.toDouble()),
            Point(br.x.toDouble(), br.y.toDouble()), Point(bl.x.toDouble(), bl.y.toDouble())
        )
        val to = MatOfPoint2f(
            Point(0.0, 0.0), Point(ow.toDouble(), 0.0),
            Point(ow.toDouble(), oh.toDouble()), Point(0.0, oh.toDouble())
        )
        val m = Imgproc.getPerspectiveTransform(from, to)
        val out = Mat()
        Imgproc.warpPerspective(srcMat, out, m, Size(ow.toDouble(), oh.toDouble()), Imgproc.INTER_CUBIC)
        return toBitmap(out)
    }

    /** Applies a "scanned look" filter. */
    fun applyFilter(src: Bitmap, filter: ScanFilter): Bitmap {
        if (filter == ScanFilter.ORIGINAL) return src

        val rgba = Mat()
        Utils.bitmapToMat(src, rgba)
        val rgb = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)

        val out = Mat()
        when (filter) {
            ScanFilter.BW -> {
                val gray = Mat()
                Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
                Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
                Imgproc.adaptiveThreshold(
                    gray, out, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY, 35, 12.0
                )
            }
            ScanFilter.GRAY -> {
                val flat = flatten(rgb)
                Imgproc.cvtColor(flat, out, Imgproc.COLOR_RGB2GRAY)
                out.convertTo(out, -1, 1.25, -25.0)
            }
            else -> { // MAGIC: remove shadows / uneven light, whiten paper, keep colour
                val flat = flatten(rgb)
                flat.convertTo(out, -1, 1.2, -25.0)
            }
        }
        return toBitmap(out)
    }

    /** Divides the image by an estimate of its background -> evenly lit white paper. */
    private fun flatten(rgb: Mat): Mat {
        val bg = Mat()
        Imgproc.dilate(rgb, bg, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0)))
        Imgproc.medianBlur(bg, bg, 21)
        val out = Mat()
        Core.divide(rgb, bg, out, 255.0)
        return out
    }

    private fun toBitmap(m: Mat): Bitmap {
        val bmp = Bitmap.createBitmap(m.cols(), m.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(m, bmp)
        return bmp
    }
}
