package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.domain.model.DashcamCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashcamFilenameParserTest {
    private val parser = DashcamFilenameParser()

    @Test
    fun parsesFrontVideoFilename() {
        val parsed = parser.parse("20260713_192150_34_f.ts")
        assertEquals(DashcamCamera.FRONT, parsed?.camera)
        assertEquals("34", parsed?.streamId)
        assertEquals("ts", parsed?.extension)
        assertEquals(2026, parsed?.localDateTime?.year)
    }

    @Test
    fun parsesRearVideoFilename() {
        val parsed = parser.parse("/mnt/sdcard/VIDEO_B/20260713_192151_31_b.ts")
        assertEquals(DashcamCamera.REAR, parsed?.camera)
        assertEquals("31", parsed?.streamId)
    }

    @Test
    fun parsesPictureFilename() {
        val parsed = parser.parse("20260713_173815_34_f.jpg")
        assertEquals(DashcamCamera.FRONT, parsed?.camera)
        assertEquals("jpg", parsed?.extension)
    }

    @Test
    fun handlesInvalidFilename() {
        assertNull(parser.parse("bad-file.ts"))
    }
}
