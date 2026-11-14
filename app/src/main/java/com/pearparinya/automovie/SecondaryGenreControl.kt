package com.pearparinya.automovie

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

internal class SecondaryGenreControl(context: Context, panel: LinearLayout, factual: Boolean) {
    private val ids = listOf("", "cinema_horror", "cinema_fantasy", "cinema_mystery", "cinema_creature", "cinema_relationships")
    private val spinner = Spinner(context)
    private val disabled = factual
    init {
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
            listOf("ไม่มีแนวรอง", "ผีและสยองขวัญ", "แฟนตาซี", "สืบสวน", "สัตว์ประหลาด", "ดราม่าครอบครัว"))
        if (!factual) {
            panel.addView(TextView(context).apply { text = "แนวรอง (เลือกผสมได้)"; setTextColor(android.graphics.Color.WHITE) })
            panel.addView(spinner)
        }
    }
    val selectedId: String get() = if (disabled) "" else ids.getOrElse(spinner.selectedItemPosition) { "" }
}
