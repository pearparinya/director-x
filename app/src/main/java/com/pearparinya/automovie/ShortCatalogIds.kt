package com.pearparinya.automovie

import java.io.File
import org.json.JSONObject

/** Display aliases only: legacy IDs and image/story files remain unchanged. */
internal class ShortCatalogIds(private val file: File) {
    /** Merge archived aliases only when neither a key nor a short ID conflicts. */
    fun mergeBackup(source: File) {
        if(!source.isFile) return
        fun parse(f: File): Map<String,String> {
            val data=if(f.isFile) JSONObject(f.readText()) else JSONObject()
            return data.keys().asSequence().associateWith { data.getString(it) }.also { values ->
                require(values.values.size==values.values.toSet().size && values.values.all { it.matches(Regex("[HAGDTUP][0-9]{3,}")) }) { "ทะเบียนรหัสในชุดสำรองไม่ถูกต้อง" }
            }
        }
        val current=parse(file); val incoming=parse(source)
        require(incoming.all { (key,value) -> (current[key]==null || current[key]==value) && current.none { (k,v) -> k!=key && v==value } }) { "รหัสสั้นในชุดสำรองชนกับเครื่องนี้ กรุณาตรวจทะเบียนก่อนกู้คืน" }
        val merged=JSONObject(); (current+incoming).toSortedMap().forEach { (k,v) -> merged.put(k,v) }
        file.parentFile?.mkdirs(); val pending=File(file.parentFile,file.name+".pending")
        try { pending.writeText(merged.toString(2)); check(pending.renameTo(file)) { "กู้คืนทะเบียนรหัสไม่สำเร็จ" } } finally { pending.delete() }
    }
    fun assign(items: List<Pair<String,String>>): Map<String,String> {
        val stored=if(file.isFile) JSONObject(file.readText()) else JSONObject()
        val existing=stored.keys().asSequence().associateWith { stored.getString(it) }.toMutableMap()
        require(existing.values.size==existing.values.toSet().size && existing.values.all { it.matches(Regex("[HAGDTUP][0-9]{3,}")) }) { "ทะเบียนรหัสตัวละครไม่ถูกต้อง" }
        var changed=false
        items.distinctBy { it.first }.forEach { (legacy,category) ->
            require(legacy.isNotBlank())
            if(!existing.containsKey(legacy)) {
                val prefix=when(category) {
                    "มนุษย์" -> "H"; "สัตว์" -> "A"; "ผี วิญญาณ และภูต" -> "G"
                    "อสูรและสัตว์ประหลาด" -> "D"; "เทพและผู้พิทักษ์" -> "T"
                    "อาวุธและอุปกรณ์" -> "P"; else -> "U"
                }
                val next=(existing.values.filter { it.startsWith(prefix) }.mapNotNull { it.drop(1).toIntOrNull() }.maxOrNull() ?: 0)+1
                existing[legacy]=prefix+next.toString().padStart(3,'0'); changed=true
            }
        }
        if(changed) {
            val next=JSONObject(); existing.toSortedMap().forEach { (key,value) -> next.put(key,value) }
            file.parentFile?.mkdirs(); val pending=File(file.parentFile,file.name+".pending")
            try { pending.writeText(next.toString(2)); check(pending.renameTo(file)) { "บันทึกทะเบียนรหัสไม่สำเร็จ" } } finally { pending.delete() }
        }
        return existing
    }
}
