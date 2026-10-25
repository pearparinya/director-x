package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class BodyReferencePolicyTest {
    @Test fun singleCheckUsesOnlyNamedIdentityAndActualSceneOutfit() {
        val prompt=BodyReferencePolicy.singleCheck("เมย์","MIND_MASTER.png","ผมบ๊อบสีน้ำตาล","เสื้อเชิ้ตเขียวกับกางเกงน้ำตาล","9:16")
        assertTrue(prompt.contains("MIND_MASTER.png"))
        assertTrue(prompt.contains("เสื้อเชิ้ตเขียวกับกางเกงน้ำตาล"))
        assertTrue(prompt.contains("ทรวงอก เอว สะโพก"))
        assertTrue(prompt.contains("ไม่เดาขนาดจริง"))
        assertTrue(prompt.contains("ไม่แทนที่ MASTER ต้นฉบับ"))
        assertFalse(prompt.contains("RINLADA"))
        assertFalse(prompt.contains("100%"))
    }
    @Test fun changingSceneMasterOrClothesInvalidatesHumanReview() {
        val stamp=BodyReviewStamp("imageA","masterA","shirtA")
        assertTrue(stamp.matches("imageA","masterA","shirtA"))
        assertFalse(stamp.matches("imageB","masterA","shirtA"))
        assertFalse(stamp.matches("imageA","masterB","shirtA"))
        assertFalse(stamp.matches("imageA","masterA","shirtB"))
        assertFalse(BodyReviewStamp("","","shirtA").matches("","","shirtA"))
    }
}
