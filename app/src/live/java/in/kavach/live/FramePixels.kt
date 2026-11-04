package `in`.kavach.live

import java.nio.ByteBuffer

/** ImageReader may omit padding after the last row. Do not copy it into a padded bitmap. */
internal fun rgbaPixels(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): IntArray {
    require(width in 1..LivePolicy.MAX_IMAGE_EDGE && height in 1..LivePolicy.MAX_IMAGE_EDGE)
    require(pixelStride == 4 && rowStride >= width * pixelStride)
    val first = buffer.position()
    require((height - 1L) * rowStride + width.toLong() * pixelStride <= buffer.remaining())
    return IntArray(width * height) { index ->
        val start = first + (index / width) * rowStride + (index % width) * pixelStride
        val r = buffer.get(start).toInt() and 255
        val g = buffer.get(start + 1).toInt() and 255
        val b = buffer.get(start + 2).toInt() and 255
        val a = buffer.get(start + 3).toInt() and 255
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
