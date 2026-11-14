package com.pearparinya.automovie

import java.io.File
import org.json.JSONObject

/** Display aliases only: legacy IDs and image/story files remain unchanged. */
internal class ShortCatalogIds(private val file: File) {
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
