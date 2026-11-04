package `in`.kavach.live

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class FramePixelsTest {
    @Test fun strideAndUnpaddedLastRowPreserved() {
        val bytes = byteArrayOf(1, 2, 3, -1, 0, 0, 0, 0, 4, 5, 6, -1)
        assertArrayEquals(intArrayOf(0xff010203.toInt(), 0xff040506.toInt()), rgbaPixels(ByteBuffer.wrap(bytes), 1, 2, 8, 4))
    }
    @Test fun bufferPositionRespected() {
        val buffer = ByteBuffer.wrap(byteArrayOf(0, 1, 2, 3, -1)).apply { position(1) }
        assertArrayEquals(intArrayOf(0xff010203.toInt()), rgbaPixels(buffer, 1, 1, 4, 4))
    }
    @Test fun incompleteImageRejected() {
        assertTrue(runCatching { rgbaPixels(ByteBuffer.allocate(3), 1, 1, 4, 4) }.isFailure)
    }
    @Test fun oversizedImageRejected() {
        assertTrue(runCatching { rgbaPixels(ByteBuffer.allocate(4), 961, 1, 3844, 4) }.isFailure)
    }
}
