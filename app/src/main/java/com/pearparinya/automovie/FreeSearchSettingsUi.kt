package com.pearparinya.automovie

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** One configuration surface shared by app settings and the product flow. */
internal object FreeSearchSettingsUi {
    fun show(activity: Activity): AlertDialog {
        val settings=FreeSearchSettings(activity)
        val panel=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(24,16,24,16) }
        panel.addView(TextView(activity).apply {
            text="ตั้งบัญชีแต่ละบริการเป็นแผนฟรีและปิดการตัดเงินอัตโนมัติ • ตัวนับนี้เฉพาะเครื่อง ไม่ยืนยันยอดหรือป้องกันค่าบริการจากบัญชี"
            setTextColor(Color.WHITE); textSize=14f; setPadding(8,8,8,16)
        })
        lateinit var dialog: AlertDialog
        fun refresh() {
            while(panel.childCount>1) panel.removeViewAt(1)
            FreeSearchSettings.PROVIDERS.forEachIndexed { index, (id,label) ->
                val card=LinearLayout(activity).apply {
                    orientation=LinearLayout.VERTICAL; setPadding(16,14,16,14)
                    background=GradientDrawable().apply { setColor(Color.rgb(8,48,78)); cornerRadius=18f }
                }
                val ready=settings.key(id).isNotBlank()
                card.addView(TextView(activity).apply { text="${index+1}  $label • ${if(ready) "บันทึกคีย์แล้ว ✓" else "ยังไม่ได้ตั้งค่า"}"; textSize=17f; setTextColor(Color.rgb(255,196,77)); setTypeface(null,Typeface.BOLD) })
                card.addView(TextView(activity).apply { text="บนเครื่อง ${settings.used(id)}/${settings.limit(id)} ครั้ง${if(id=="langsearch") "/วัน" else "/เดือน"} • ตรวจยอดจริงในบัญชี"; setTextColor(Color.WHITE) })
                card.addView(Button(activity).apply {
                    text="🔑 ตั้งค่า / เพดาน"; isAllCaps=false
                    setOnClickListener {
                        val choices=arrayOf("บันทึกคีย์ใหม่", "ตั้งเพดานบนเครื่อง", "ลบคีย์")
                        AlertDialog.Builder(activity).setTitle("$label • ตั้งค่า").setItems(choices) { _,which ->
                            when(which) {
                                0 -> {
                                    val input=EditText(activity).apply { hint="วาง API Key ใหม่"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
                                    AlertDialog.Builder(activity).setTitle("$label • บันทึกคีย์").setView(input).setPositiveButton("บันทึก") { _,_ ->
                                        runCatching { settings.save(id,input.text.toString()) }.onFailure { AlertDialog.Builder(activity).setMessage("ตรวจ API Key อีกครั้ง").setPositiveButton("ตกลง",null).show() }
                                        refresh()
                                    }.setNegativeButton("ยกเลิก",null).show()
                                }
                                1 -> {
                                    val input=EditText(activity).apply { inputType=InputType.TYPE_CLASS_NUMBER; setText(settings.limit(id).toString()) }
                                    AlertDialog.Builder(activity).setTitle("เพดานจำนวนครั้งบนเครื่อง").setMessage("เฉพาะเครื่องนี้ • ไม่ใช่ยอดเครดิตจริง • ตั้ง 0 เพื่อหยุดบริการ • สูงสุด ${settings.ceiling(id)}")
                                        .setView(input).setPositiveButton("บันทึก") { _,_ ->
                                            val amount=input.text.toString().toIntOrNull()
                                            if(amount==null || runCatching { settings.setLimit(id,amount) }.isFailure) AlertDialog.Builder(activity).setMessage("กรุณาระบุจำนวนตั้งแต่ 0 ถึง ${settings.ceiling(id)}").setPositiveButton("ตกลง",null).show()
                                            refresh()
                                        }.setNegativeButton("ยกเลิก",null).show()
                                }
                                else -> AlertDialog.Builder(activity).setTitle("ลบคีย์ $label?").setPositiveButton("ลบ") { _,_ -> settings.clear(id); refresh() }.setNegativeButton("ยกเลิก",null).show()
                            }
                        }.setNegativeButton("ยกเลิก",null).show()
                    }
                })
                card.addView(Button(activity).apply {
                    text="▶ ทดสอบ $label"; isAllCaps=false
                    setOnClickListener {
                        if(!ready) { AlertDialog.Builder(activity).setMessage("ยังไม่ได้บันทึกคีย์ $label").setPositiveButton("ตกลง",null).show(); return@setOnClickListener }
                        AlertDialog.Builder(activity).setTitle("ทดสอบ $label").setMessage("การทดสอบใช้โควตาค้นหา 1 คำขอ • ยอดที่นับบนเครื่องอาจไม่ตรงกับบัญชี")
                            .setPositiveButton("เริ่มทดสอบ") { _,_ -> test(activity,id,label) { refresh() } }.setNegativeButton("ยกเลิก",null).show()
                    }
                })
                panel.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=12 })
            }
        }
        refresh()
        dialog=AlertDialog.Builder(activity).setTitle("🔎 API ค้นหาสินค้า").setView(ScrollView(activity).apply { addView(panel) }).setPositiveButton("ปิด",null).create()
        dialog.show()
        return dialog
    }
    private fun test(activity: Activity, id: String, label: String, refresh: () -> Unit) {
        val control=ProductSearchControl(); val handler=Handler(Looper.getMainLooper()); val start=SystemClock.elapsedRealtime()
        val progress=AlertDialog.Builder(activity).setTitle("กำลังทดสอบ $label").setMessage("กำลังเชื่อมต่อ…").setNegativeButton("ยกเลิก",null).create()
        val tick=object: Runnable { override fun run() { if(!progress.isShowing) return; val secs=(SystemClock.elapsedRealtime()-start)/1000; progress.setMessage("กำลังทดสอบ $label • เวลา %02d:%02d".format(secs/60,secs%60)); handler.postDelayed(this,1000) } }
        progress.setOnDismissListener { control.cancel(); handler.removeCallbacks(tick) }
        progress.show(); handler.post(tick)
        Thread {
            val outcome=runCatching { FreeProductSearch.search(activity.applicationContext,id,"แก้วน้ำ",1,control) }
            activity.runOnUiThread {
                if(control.cancelled() || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                progress.dismiss(); refresh()
                val message=outcome.fold({ "เชื่อมต่อ API สำเร็จ ✓\nพบลิงก์สินค้า ${it.size} รายการ • ไม่รับประกันว่าสินค้ายังจำหน่าย" }, { ProductSearchRotation.friendly(it) })
                AlertDialog.Builder(activity).setTitle("ผลทดสอบ $label").setMessage(message).setPositiveButton("ตกลง",null).show()
            }
        }.start()
    }
}
