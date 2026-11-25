package com.pearparinya.automovie

import android.content.Intent
import android.content.ClipboardManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SocialSeoUiTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val ctx=ins.targetContext
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun main(block:()->Unit) { ins.runOnMainSync { block() }; ins.waitForIdleSync() }
    @Test fun editorCopyNavigationAndDraftIsolation() {
        val prefs=ctx.getSharedPreferences("studio",0)
        val original=prefs.getString("project","draft")
        val id=UUID.randomUUID().toString()
        val other=UUID.randomUUID().toString()
        val drafts=ctx.getSharedPreferences("social_post_drafts",0)
        val story=JSONObject().put("title","ทดสอบ SEO").put("episodes",JSONArray()
            .put(JSONObject().put("ep",1).put("title","ตอนหนึ่ง").put("scenes",JSONArray()))
            .put(JSONObject().put("ep",2).put("title","ตอนสอง").put("scenes",JSONArray())))
        val snapshot=story.toString()
        val storyFile=java.io.File(ctx.filesDir,"studio/"+id+"/story.json")
        storyFile.parentFile!!.mkdirs()
        storyFile.writeText(snapshot)
        val originalBytes=storyFile.readBytes()
        var activity:StudioActivity?=null
        var dialog:AlertDialog?=null
        fun launch(project:String) {
            prefs.edit().putString("project",project).commit()
            activity=ins.startActivitySync(Intent(ctx,StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity
            ins.waitForIdleSync()
        }
        fun open(s:JSONObject=story) {
            main {
                val method=StudioActivity::class.java.getDeclaredMethod("postDetails",JSONObject::class.java)
                method.isAccessible=true
                dialog=method.invoke(activity,s) as AlertDialog
            }
        }
        fun button(label:String)=views(dialog!!.window!!.decorView).filterIsInstance<Button>().first { it.text.toString()==label }
        fun edit()=views(dialog!!.window!!.decorView).filterIsInstance<EditText>().single()
        fun click(label:String) { main { assertTrue(button(label).isEnabled); button(label).performClick() } }
        fun set(value:String) { main { edit().setText(value) } }
        fun expect(value:String) { main { assertEquals(value,edit().text.toString()) } }
        fun close() { main { dialog?.dismiss(); dialog=null } }
        fun finish() { main { activity?.finish(); activity=null } }
        try {
            launch(id); open()
            main { assertFalse(button("ก่อนหน้า").isEnabled) }
            val fields=listOf("Facebook" to "FACEBOOK POST","TikTok" to "TIKTOK CAPTION","YouTube" to "SEO TITLE","Description" to "DESCRIPTION","Tags" to "SEO TAGS","Instagram" to "INSTAGRAM CAPTION")
            for((tab,field) in fields) {
                click(tab); val value="ข้อความทดสอบ "+field; set(value)
                click("COPY "+field)
                main {
                    val clipboard=activity!!.getSystemService(ClipboardManager::class.java)
                    assertEquals(value,clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                }
            }
            click("ถัดไป")
            main { assertFalse(button("ถัดไป").isEnabled); assertTrue(edit().text.toString().contains("EP.2")) }
            set("ตอนสองแยกข้อมูล")
            click("ก่อนหน้า"); expect("ข้อความทดสอบ INSTAGRAM CAPTION")
            click("Facebook"); expect("ข้อความทดสอบ FACEBOOK POST")
            for ((sample, expected) in listOf("ทดสอบ" to 5, "ภาษาไทย" to 7, "" to 0, "ก".repeat(351) to 351)) {
                set(sample)
                main {
                    val labels=views(dialog!!.window!!.decorView).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
                    assertTrue("Expected counter $expected / 350; visible labels: $labels", labels.contains("$expected / 350 ตัวอักษร"))
                }
            }
            set("ข้อความทดสอบ FACEBOOK POST")
            click("ถัดไป"); main { assertTrue(button("Facebook").isSelected) }
            click("ก่อนหน้า"); expect("ข้อความทดสอบ FACEBOOK POST")
            click("ย้อนกลับ"); main { assertFalse(dialog!!.isShowing) }; dialog=null
            open(); expect("ข้อความทดสอบ FACEBOOK POST")
            close(); finish()
            launch(id); open(); expect("ข้อความทดสอบ FACEBOOK POST")
            close(); finish()
            launch(other); open()
            main { assertNotEquals("ข้อความทดสอบ FACEBOOK POST",edit().text.toString()) }
            close()
            open(JSONObject().put("title","เรื่องเก่า"))
            main { assertFalse(button("ก่อนหน้า").isEnabled); assertFalse(button("ถัดไป").isEnabled) }
            close()
            assertEquals(snapshot,story.toString())
            assertArrayEquals(originalBytes,storyFile.readBytes())
        } finally {
            if(dialog!=null) close()
            if(activity!=null) finish()
            prefs.edit().putString("project",original).commit()
            val cleanup=drafts.edit()
            drafts.all.keys.filter { it.contains(id)||it.contains(other) }.forEach { cleanup.remove(it) }
            cleanup.commit()
            java.io.File(ctx.filesDir,"studio/"+id).deleteRecursively()
            java.io.File(ctx.filesDir,"studio/"+other).deleteRecursively()
        }
    }
}
