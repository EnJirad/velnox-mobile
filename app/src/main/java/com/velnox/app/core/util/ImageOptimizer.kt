package com.velnox.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Result of client-side image optimization.
 */
data class OptimizedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val filename: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OptimizedImage
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType && filename == other.filename
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + filename.hashCode()
        return result
    }
}

/**
 * Client-side image optimizer that downsamples, rotates, and compresses images
 * to WebP format before uploading to Cloudflare R2.
 *
 * Matches the Web application contract:
 * - Avatar: max 800x800, quality 85, image/webp
 * - Cover:  max 1920x600, quality 85, image/webp
 *
 * This reduces image payload from 10MB+ down to ~80-250KB, preventing timeouts,
 * out-of-memory errors, and R2 size cap rejections.
 */
object ImageOptimizer {

    suspend fun optimize(
        context: Context,
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int = 85,
    ): OptimizedImage? = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Read EXIF orientation
            val orientation = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            // 2. Decode image bounds without allocating pixel memory
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return@withContext null

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return@withContext null

            // 3. Compute inSampleSize power of 2
            var inSampleSize = 1
            while ((origWidth / inSampleSize) > maxWidth * 2 || (origHeight / inSampleSize) > maxHeight * 2) {
                inSampleSize *= 2
            }

            // 4. Decode downsampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            // 5. Apply EXIF rotation if needed
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            val orientedBitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(
                    sampledBitmap,
                    0,
                    0,
                    sampledBitmap.width,
                    sampledBitmap.height,
                    matrix,
                    true,
                ).also { if (it != sampledBitmap) sampledBitmap.recycle() }
            } else {
                sampledBitmap
            }

            // 6. Scale to target bounds maintaining aspect ratio
            val scale = minOf(
                maxWidth.toFloat() / orientedBitmap.width,
                maxHeight.toFloat() / orientedBitmap.height,
                1.0f,
            )
            val finalBitmap = if (scale < 1.0f) {
                val targetW = (orientedBitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (orientedBitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true).also {
                    if (it != orientedBitmap) orientedBitmap.recycle()
                }
            } else {
                orientedBitmap
            }

            // 7. Compress to WebP (native on API 26+)
            val outputStream = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

            val compressed = finalBitmap.compress(format, quality, outputStream)
            val resultBytes = if (compressed && outputStream.size() > 0) {
                finalBitmap.recycle()
                OptimizedImage(outputStream.toByteArray(), "image/webp", "image.webp")
            } else {
                // Fallback to JPEG if WebP encoding fails
                val jpegStream = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpegStream)
                finalBitmap.recycle()
                OptimizedImage(jpegStream.toByteArray(), "image/jpeg", "image.jpg")
            }

            resultBytes
        }.getOrNull()
    }
}
