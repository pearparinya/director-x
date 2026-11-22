package com.pearparinya.automovie

import org.json.JSONObject

/** Shared, conservative recovery state for the home card and recovery dialog. */
internal object MiniStoryRecovery {
    fun characterNameIssue(status: JSONObject?): Boolean =
        status?.optString("message")?.contains("พบชื่อภาษาอังกฤษ")==true

    fun needsAttention(status: JSONObject?, alive: Boolean): Boolean {
        if(status==null || alive) return false
        return status.optString("state") in setOf("failed","paused","awaiting_outline") ||
            characterNameIssue(status) || status.optString("state")=="running"
    }

    fun reason(status: JSONObject?): String = when {
        characterNameIssue(status) -> "ชื่อตัวละครไม่ตรงกับบท"
        status?.optString("state")=="awaiting_outline" -> "รอเขียนบทต่อ"
        status?.optString("state")=="paused" -> "งานหยุดชั่วคราว"
        status?.optString("state")=="failed" -> "งานหยุดเพราะข้อผิดพลาด"
        else -> "งานยังไม่เสร็จ"
    }

    fun explanation(status: JSONObject?): String = when {
        characterNameIssue(status) -> "ตรวจชื่อกับข้อมูล MASTER ก่อนทำต่อ • ซีนที่ผ่านแล้วจะยังคงอยู่"
        else -> "เปิดรายละเอียดงานเพื่อตรวจสอบก่อนทำต่อ"
    }
}
