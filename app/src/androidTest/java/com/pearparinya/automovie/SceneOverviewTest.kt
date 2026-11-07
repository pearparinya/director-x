package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SceneOverviewTest {
    @Test fun summaryUsesCurrentSceneAndHandlesMissingFields() {
        val scene=JSONObject().put("location","ห้องรับแขก")
            .put("characters",JSONArray().put(JSONObject().put("name","ปิ่นมณี")).put(JSONObject().put("name","นที")))
            .put("action","ปิ่นมณีมองนที").put("speaker","ปิ่นมณี").put("dialogue","เธอรู้ใช่ไหม")
        val first=SceneOverview.detail(scene,"EP01_SC001")
        assertTrue(first.contains("ห้องรับแขก")); assertTrue(first.contains("ปิ่นมณี")); assertTrue(first.contains("เธอรู้ใช่ไหม"))
        assertTrue(first.contains("EP01_SC001")); assertTrue(SceneOverview.short(scene).contains("ปิ่นมณีมองนที"))
        scene.put("action","นทีเดินออกไป")
        assertTrue(SceneOverview.detail(scene,"EP01_SC001").contains("นทีเดินออกไป"))
        assertFalse(SceneOverview.short(scene).contains("ปิ่นมณีมองนที"))
        val missing=SceneOverview.detail(JSONObject(),"EP01_SC002")
        assertTrue(missing.contains("ไม่ได้ระบุ")); assertTrue(missing.contains("ไม่มีบทพูดหรือคำบรรยายที่ระบุ"))
    }
}
