package com.example.esp32app.vision

import org.junit.Assert.*
import org.junit.Test

class ScreenMappingTest {

    /** Layout do PC de teste: DP-2 1920x1200 em 0,0 e HDMI capturado 1920x1080 em 1920,0. */
    private val rightMonitor = ScreenMapping(3840, 1200, 1920, 0, 1920, 1080)

    @Test
    fun singleMonitorUsesWholeRange() {
        val m = ScreenMapping.SINGLE_MONITOR
        assertTrue(m.isSingleMonitor)
        assertEquals(Pair(0, 0), m.toHid(0f, 0f))
        assertEquals(Pair(16383, 16383), m.toHid(0.5f, 0.5f))
        assertEquals(Pair(32767, 32767), m.toHid(1f, 1f))
    }

    @Test
    fun capturedMonitorOnTheRightMapsToRightHalf() {
        assertFalse(rightMonitor.isSingleMonitor)
        // Centro do HDMI = pixel 2880,540 da área 3840x1200
        assertEquals(Pair(24575, 14745), rightMonitor.toHid(0.5f, 0.5f))
        assertEquals(Pair(16383, 0), rightMonitor.toHid(0f, 0f))
        // Fundo do HDMI (1080) fica acima do fundo da área (1200)
        assertEquals(Pair(32767, 29490), rightMonitor.toHid(1f, 1f))
    }

    @Test
    fun capturedMonitorOnTheLeft() {
        val left = ScreenMapping(3840, 1080, 0, 0, 1920, 1080)
        assertEquals(Pair(8191, 16383), left.toHid(0.5f, 0.5f))
    }

    @Test
    fun pointsOutsideTheFrameAreClampedToTheMonitor() {
        assertEquals(rightMonitor.toHid(0f, 0f), rightMonitor.toHid(-1f, -1f))
        assertEquals(rightMonitor.toHid(1f, 1f), rightMonitor.toHid(2f, 2f))
    }

    @Test
    fun validationRejectsImpossibleLayouts() {
        assertNull(rightMonitor.validationError())
        assertNotNull(ScreenMapping(0, 1080, 0, 0, 1920, 1080).validationError())
        assertNotNull(ScreenMapping(1920, 1080, 0, 0, 0, 1080).validationError())
        assertNotNull(ScreenMapping(3840, 1080, -1920, 0, 1920, 1080).validationError())
        assertNotNull(ScreenMapping(3840, 1080, 2000, 0, 1920, 1080).validationError())
        assertNotNull(ScreenMapping(1920, 1080, 0, 100, 1920, 1080).validationError())
    }
}
