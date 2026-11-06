package com.derycode.srs.core

import com.derycode.srs.core.report.imageSize
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The logo size is read from the picture's own bytes. This used to go through
 * ImageIO, which does not exist on a phone: the Android build failed and an
 * export would have crashed on any school that had uploaded a logo.
 */
class ImageSizeTest {

    @Test fun `a png reports its own width and height`() {
        val png = byteArrayOf(
            0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10,
            0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
            0, 0, 1, 0,          // width 256
            0, 0, 0, 64          // height 64
        )
        assertEquals(256 to 64, imageSize(png))
    }

    @Test fun `a jpeg reports its own width and height`() {
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),                       // SOI
            0xFF.toByte(), 0xE0.toByte(), 0, 4, 1, 2,           // APP0, length 4 (skipped)
            0xFF.toByte(), 0xC0.toByte(), 0, 17, 8,             // SOF0, length, precision
            0, 48,                                              // height 48
            0, 96,                                              // width 96
            3, 1, 1, 0, 1, 1, 1, 1
        )
        assertEquals(96 to 48, imageSize(jpeg))
    }

    @Test fun `an unknown picture falls back to a square instead of failing`() {
        assertEquals(0 to 0, imageSize(byteArrayOf(1, 2, 3)))
    }
}
