package com.pearparinya.automovie

import android.view.View
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HiddenActionTest {
    @Test fun hiddenManualActionCanRunFromAutomaticButton() {
        var invoked=false
        val instrument=InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync {
            val manual=Button(instrument.targetContext).apply {
                visibility=View.GONE
                setOnClickListener { invoked=true }
            }
            manual.performClick()
        }
        assertTrue(invoked)
    }
}
