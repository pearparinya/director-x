package com.pearparinya.automovie

import android.app.Activity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONObject

internal class SecondaryGenreControl(private val context: Activity, panel: LinearLayout, private val factual: Boolean, private val category: String, private val situation: String) {
    private val ids = if (factual) listOf("", "", "pia_explainer", "pia_howto", "pia_story") else listOf("", "", "cinema_horror", "cinema_fantasy", "cinema_mystery", "cinema_creature", "cinema_relationships")
    private val labels = if (factual) listOf("ไม่มีแนวรอง", "AI คิดแนวรองให้อัตโนมัติ", "เล่าให้เข้าใจง่าย", "แนะนำวิธีใช้", "เล่าผ่านเหตุการณ์") else listOf("ไม่มีแนวรอง", "AI คิดแนวรองให้อัตโนมัติ", "ผีและสยองขวัญ", "แฟนตาซี", "สืบสวน", "สัตว์ประหลาด", "ดราม่าครอบครัว")
    private val spinner = Spinner(context)
    private val status = TextView(context).apply { setTextColor(android.graphics.Color.WHITE) }
    private var autoId: String? = null
    private var request = 0
    init {
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
        panel.addView(TextView(context).apply { text = "แนวรอง"; setTextColor(android.graphics.Color.WHITE) })
        panel.addView(spinner); panel.addView(status)
        spinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                request++
                if(position!=1) { autoId=null; status.text=if(position==0) "ไม่มีแนวรอง" else "แนวรอง: ${labels[position]}"; return }
                val token=request; autoId=null; status.text="กำลังให้ AI เลือกแนวรอง…"
                val providers=StoryProvider.eligible(context).filter { it.hasKey(context) }
                Thread {
                    val schema=JSONObject("""{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}""")
                    val choices=(2 until ids.size).joinToString(", ") { "${ids[it]}=${labels[it]}" }
                    val chosen=providers.firstNotNullOfOrNull { provider ->
                        runCatching {
                            val client=StoryClientFactory.create(provider,provider.key(context),provider.model(context),{false},automaticRetry=false,context=context)
                            client.useProposalTimeouts()
                            client.generate("Choose exactly one suitable secondary genre ID from [$choices] for primary category $category and situation $situation. The primary category must remain dominant. For factual material do not invent facts. Return ID only in JSON.",schema).optString("id").takeIf { it in ids.drop(2) }
                        }.getOrNull()
                    }
                    context.runOnUiThread {
                        if(request!=token || context.isFinishing || context.isDestroyed) return@runOnUiThread
                        autoId=chosen
                        status.text=if(chosen==null) "AI เลือกไม่สำเร็จ • เลือกแนวรองเองหรือลองอีกครั้ง" else "AI เลือกแนวรอง: ${labels[ids.indexOf(chosen)]}"
                    }
                }.start()
            }
        }
    }
    val ready: Boolean get() = spinner.selectedItemPosition!=1 || autoId!=null
    val selectedId: String get() = if(spinner.selectedItemPosition==1) autoId.orEmpty() else ids.getOrElse(spinner.selectedItemPosition) { "" }
}
