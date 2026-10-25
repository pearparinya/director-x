package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class ImagePromptPolicyTest {
    @Test fun referenceAppealIsConsistentWithoutChangingWardrobeOrNewIdentityRules() {
        val prompts=listOf(ImagePromptPolicy.productionMaster("เมย์","MIND_MASTER.png",""),
            ImagePromptPolicy.scene("เมย์ | ชุด: เสื้อคอสูงสีฟ้า","ห้องนั่งเล่น","ถือแก้วน้ำ"),
            ImagePromptPolicy.edit("ภาพ MASTER","ฉากหลังเป็นห้องครัว"),
            ImagePromptPolicy.coverReference("ภาพฉาก"),
            BodyReferencePolicy.singleCheck("เมย์","MIND_MASTER.png","","เสื้อคอสูงสีฟ้า","9:16"))
        prompts.forEach {
            assertEquals(1,it.split(ImagePromptPolicy.referenceAppeal).size-1)
            assertFalse(it.contains("100%"))
        }
        assertTrue(prompts[1].contains("เสื้อคอสูงสีฟ้า"))
        assertTrue(prompts[1].contains("เหตุการณ์ต่อเนื่องให้ใช้ชุดเดิม"))
        assertTrue(prompts[2].contains("เปลี่ยนเฉพาะ: ฉากหลังเป็นห้องครัว"))
        assertFalse(ImagePromptPolicy.newCharacter("ภาพแนบ","ร้านกาแฟ").contains(ImagePromptPolicy.referenceAppeal))
    }
    @Test fun preservingWardrobeDoesNotAlsoRequestANewOutfit() {
        val prompt=ImagePromptPolicy.scene("กวิน → GAWIN_MASTER.png", "ห้องนั่งเล่น", "ยืนผ่อนคลาย",changeWardrobe=false)
        assertTrue(prompt.contains("คงเครื่องแต่งกายตามภาพอ้างอิง"))
        assertFalse(prompt.contains("ให้เปลี่ยนเสื้อผ้า"))
    }

    @Test fun sceneUsesUserBriefWithoutRepeatedAnatomyInstructions() {
        val prompt=ImagePromptPolicy.scene("เมย์ → MIND_MASTER.png | ชุด: เสื้อประชุมสีเขียว", "ห้องประชุม", "ถือแฟ้ม")
        assertTrue(prompt.contains("สร้างภาพฉากละครจากไฟล์อ้างอิงที่แนบ"))
        assertTrue(prompt.contains("เหตุการณ์ต่อเนื่องให้ใช้ชุดเดิม"))
        assertTrue(prompt.contains(ImagePromptPolicy.referenceAppeal))
        assertFalse(prompt.contains("100%"))
        assertTrue(prompt.contains("เสื้อประชุมสีเขียว"))
        assertTrue(prompt.contains("MIND_MASTER.png"))
        assertFalse(prompt.contains("ความสัมพันธ์ของไหล่ ทรวงอก"))
    }

    @Test fun productionReferenceSeparatesIdentityFromStandardClothesAndRequiresActualReview() {
        val prompt=ImagePromptPolicy.productionMaster("รินรดา","RINLADA_MASTER.png","ผมยาวลอนสีเข้ม")
        assertTrue(prompt.contains("RINLADA_MASTER.png"))
        assertTrue(prompt.contains("ผมยาวลอนสีเข้ม"))
        assertTrue(prompt.contains("เลือกเสื้อผ้าที่สวยงามเหมาะสม"))
        assertTrue(prompt.contains("ศีรษะถึงเข่า"))
        assertTrue(prompt.contains("ก่อนเลือกเป็น MASTER ใหม่"))
        assertTrue(prompt.contains("ไม่ขยายหรือลดสัดส่วนเอง"))
        assertFalse(prompt.contains("100%"))
    }
    @Test fun actionFramingAndNightLightingOverrideGenericPortraitDefaults() {
        val prompt=ImagePromptPolicy.scene("รินรดา → RINLADA_MASTER.jpg | ชุด: เดรสกรมท่า", "ห้องนอนกลางคืน", "คุกเข่าหยิบซองจากใต้เตียง",
            aspect="9:16",framing="เต็มตัว เห็นมือและกล่องใต้เตียง",lighting="แสงโคมไฟหัวเตียง",filename="EP01_SC001.png")
        assertTrue(prompt.contains("เต็มตัว เห็นมือและกล่องใต้เตียง"))
        assertTrue(prompt.contains("แสงโคมไฟหัวเตียง"))
        assertTrue(prompt.contains("9:16"))
        assertFalse(prompt.contains("waist-up"))
        assertFalse(prompt.contains("แสงธรรมชาติ"))
        assertTrue(prompt.indexOf("ตัวละครและชุด:") < prompt.indexOf("สถานที่:"))
        assertTrue(prompt.indexOf("สถานที่:") < prompt.indexOf("การกระทำ:"))
    }
    @Test fun wardrobeOnlyEditKeepsSourceSceneAndDoesNotInventANewLocation() {
        val prompt=ImagePromptPolicy.edit("ภาพ MASTER", "เสื้อผ้าถักสีดำพอดีตัว")
        assertTrue(prompt.contains("เปลี่ยนเฉพาะ: เสื้อผ้าถักสีดำพอดีตัว"))
        assertTrue(prompt.contains("องค์ประกอบที่ไม่ได้ขอเปลี่ยน"))
        assertFalse(prompt.contains("สถานที่:"))
        assertFalse(prompt.contains("100%"))
    }
    @Test fun newCharacterAndCoverHaveDistinctIdentityAndTextRules() {
        val newPerson=ImagePromptPolicy.newCharacter("GAWIN_MASTER.png", "ร้านกาแฟ")
        assertTrue(newPerson.contains("คนใหม่"))
        assertTrue(newPerson.contains("เฉพาะสไตล์ภาพ"))
        val cover=ImagePromptPolicy.coverReference("ภาพฉาก EP01_SC001")
        assertTrue(cover.contains("พื้นที่ข้อความ"))
        assertFalse(cover.contains("ไม่มีข้อความ"))
    }
}
