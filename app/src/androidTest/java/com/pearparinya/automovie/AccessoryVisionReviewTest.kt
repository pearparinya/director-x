package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessoryVisionReviewTest {
    private fun observation(side:String, visible:Boolean=true, ambiguous:Boolean=false)=JSONObject()
        .put("character_name","นายเปีย")
        .put("observed_side",side)
        .put("watch_visible",visible)
        .put("mirrored_or_ambiguous",ambiguous)
    @Test fun exactAnatomicalSideCanPass() {
        assertEquals(AccessoryVisionReview.Decision.PASS,
            AccessoryVisionReview.evaluate("นายเปีย","right",observation("right")).decision)
    }
    @Test fun oppositeWristFails() {
        assertEquals(AccessoryVisionReview.Decision.FAIL,
            AccessoryVisionReview.evaluate("นายเปีย","right",observation("left")).decision)
    }
    @Test fun hiddenOrAmbiguousWatchRequiresReview() {
        assertEquals(AccessoryVisionReview.Decision.REVIEW,
            AccessoryVisionReview.evaluate("นายเปีย","right",observation("none",false)).decision)
        assertEquals(AccessoryVisionReview.Decision.REVIEW,
            AccessoryVisionReview.evaluate("นายเปีย","right",observation("right",true,true)).decision)
    }
    @Test fun mismatchedIdentityRequiresReview() {
        assertEquals(AccessoryVisionReview.Decision.REVIEW,
            AccessoryVisionReview.evaluate("คนอื่น","right",observation("right")).decision)
    }
}
