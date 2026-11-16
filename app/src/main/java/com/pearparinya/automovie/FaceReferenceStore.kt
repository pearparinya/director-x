package com.pearparinya.automovie

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.FaceDetector
import java.io.File
import java.util.UUID

/** Keep reviewed crops keyed by original image bytes; never overwrite either original or prior crops. */
internal class FaceReferenceStore(private val root: File) {
    private fun directory(master: File): File = File(root,MasterLibrary.digest(master.readBytes()))
    fun latest(master: File): File? = directory(master).listFiles().orEmpty()
        .filter { it.isFile && it.extension.equals("png",true) }
        .maxWithOrNull(compareBy<File> { it.lastModified() }.thenBy { it.name })
    /**
     * Reuse a reviewed crop when present; otherwise attempt an on-device face detection.
     * Never substitute an invented face or silently crop an arbitrary part of the MASTER.
     * If detection is uncertain, return null and request manual review.
     */
    fun prepare(master: File): File? {
        latest(master)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(master.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = generateSequence(1) { it * 2 }
            .first { bounds.outWidth / it <= 1200 && bounds.outHeight / it <= 1200 }
        val source = BitmapFactory.decodeFile(master.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: return null
        val width = source.width - source.width % 2
        if (width < 2 || source.height < 2) { source.recycle(); return null }
        val bitmap = Bitmap.createBitmap(source, 0, 0, width, source.height)
            .copy(Bitmap.Config.RGB_565, false)
        if (bitmap !== source) source.recycle()
        try {
            val faces = arrayOfNulls<FaceDetector.Face>(4)
            val count = FaceDetector(bitmap.width, bitmap.height, faces.size).findFaces(bitmap, faces)
            // A three-view sheet may show multiple faces; do not guess which view is frontal.
            if (count != 1) return null
            val face = faces[0] ?: return null
            if (face.confidence() < FaceDetector.Face.CONFIDENCE_THRESHOLD) return null
            val midpoint = android.graphics.PointF()
            face.getMidPoint(midpoint)
            val radius = face.eyesDistance() * 1.65f
            if (radius < 12f) return null
            val left = (midpoint.x - radius).toInt().coerceAtLeast(0)
            val right = (midpoint.x + radius).toInt().coerceAtMost(bitmap.width)
            val top = (midpoint.y - radius * 1.15f).toInt().coerceAtLeast(0)
            val bottom = (midpoint.y + radius * 1.55f).toInt().coerceAtMost(bitmap.height)
            if (right - left < 40 || bottom - top < 40) return null
            val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            try {
                val target = newFile(master)
                target.parentFile?.mkdirs()
                val pending = File(target.parentFile, target.name + ".pending")
                try {
                    pending.outputStream().use { check(crop.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    check(pending.renameTo(target))
                } finally { pending.delete() }
                return target
            } finally { crop.recycle() }
        } finally { bitmap.recycle() }
    }

    fun newFile(master: File): File = File(directory(master),"${UUID.randomUUID()}.png")
}
