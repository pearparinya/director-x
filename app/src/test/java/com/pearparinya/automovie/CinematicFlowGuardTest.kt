package com.pearparinya.automovie

import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicFlowGuardTest {
    @Test fun guardCoversPropAndOrientation() {
        val text = CinematicFlowGuard.direction()
        assertTrue(text.contains("FIRST-FRAME OBJECT LOCK"))
        assertTrue(text.contains("relative size"))
        assertTrue(text.contains("never mirror"))
        assertTrue(text.contains("hand-to-prop contact"))
    }

    @Test fun guardCoversCastMotionCameraAndAudio() {
        val text = CinematicFlowGuard.direction()
        assertTrue(text.contains("CHARACTER LOCK"))
        assertTrue(text.contains("PHYSICAL MOTION"))
        assertTrue(text.contains("LIGHTING AND COLOR"))
        assertTrue(text.contains("AUDIO"))
    }
}
