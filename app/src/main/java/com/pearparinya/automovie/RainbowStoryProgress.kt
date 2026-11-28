package com.pearparinya.automovie

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import org.json.JSONObject
import kotlin.math.roundToInt

/** Small rainbow segmented bar. Filled segments reflect saved, validated checkpoints only. */
internal class RainbowStoryProgress(context: Context) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect=RectF()
    private val palette=intArrayOf(
        Color.rgb(29,190,112), Color.rgb(156,218,57), Color.rgb(255,211,56),
        Color.rgb(255,130,43), Color.rgb(244,73,83), Color.rgb(251,81,158),
        Color.rgb(173,90,231), Color.rgb(60,147,255), Color.rgb(39,206,233)
    )
    var percent: Int=0
        private set
    var waiting: Boolean=false
        private set
    fun show(value: Int, isWaiting: Boolean) {
        percent=value.coerceIn(0,100)
        waiting=isWaiting
        invalidate()
    }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (12*resources.displayMetrics.density).roundToInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val segments=20
        val gap=3f*resources.displayMetrics.density
        val segmentWidth=(measuredWidth-(segments-1)*gap)/segments
        if(segmentWidth<=0f) return
        val filled=(percent*segments)/100
        val radius=2.5f*resources.displayMetrics.density
        for(i in 0 until segments) {
            val x=i*(segmentWidth+gap)
            rect.set(x,1f,x+segmentWidth,height-1f)
            paint.color=if(i<filled) {
                val position=i.toFloat()/(segments-1)*(palette.size-1)
                val from=position.toInt().coerceAtMost(palette.size-1)
                val to=(from+1).coerceAtMost(palette.size-1)
                val fraction=position-from
                val a=palette[from]; val b=palette[to]
                Color.rgb(
                    (Color.red(a)+(Color.red(b)-Color.red(a))*fraction).roundToInt(),
                    (Color.green(a)+(Color.green(b)-Color.green(a))*fraction).roundToInt(),
                    (Color.blue(a)+(Color.blue(b)-Color.blue(a))*fraction).roundToInt()
                )
            } else if(waiting && i==filled.coerceAtMost(segments-1)) Color.rgb(255,196,58)
            else Color.rgb(38,41,52)
            canvas.drawRoundRect(rect,radius,radius,paint)
        }
    }
}

/** UI labels are derived from the active job, never from a hard-coded AI provider. */
internal object MiniStoryProgress {
    fun provider(status: JSONObject?): String {
        val raw=status?.optString("provider").orEmpty()
        val label=when(raw.lowercase()) {
            "" -> "AI"
            "cerebras" -> "API อัตโนมัติ"
            else -> raw.replaceFirstChar { it.uppercaseChar() }
        }
        return if(status?.optBoolean("hybrid")==true) "AI Hybrid • $label" else label
    }
    fun stage(status: JSONObject?,alive: Boolean): String = when {
        status==null -> "พร้อมเขียนบท"
        status.optString("state")=="complete" -> "เขียนบทสำเร็จ"
        status.optString("state")=="failed" -> "เกิดข้อผิดพลาด"
        status.optString("state")=="paused" -> "หยุดชั่วคราว"
        status.optString("state")=="awaiting_outline" -> "รอเขียนบทต่อ"
        !alive -> "รอทำงานต่อ"
        status.optString("phase") in setOf("waiting","retry_wait") -> "กำลังรอ AI"
        status.optString("phase")=="requesting" -> "กำลังรอคำตอบ AI"
        status.optString("phase")=="saved" -> "บันทึกบทแล้ว"
        else -> "กำลังเขียนบท"
    }
    fun waiting(status: JSONObject?): Boolean =
        status?.optString("state") == "running" && status.optString("phase") in setOf("waiting","retry_wait")
    fun percent(status: JSONObject?): Int=status?.optInt("percent")?.coerceIn(0,100) ?: 0
    fun elapsed(status: JSONObject?): String=StoryStatusText.duration((status?.optLong("elapsed_ms") ?: 0L).coerceAtLeast(0L))
}
