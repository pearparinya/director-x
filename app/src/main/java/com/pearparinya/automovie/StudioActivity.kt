Warning: truncated output (original token count: 83906)
Total output lines: 3282

package com.pearparinya.automovie

import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import android.view.Gravity
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun buildChatShareIntent(text: String, uris: ArrayList<Uri>): Intent =
    Intent(if (uris.size <= 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (uris.isEmpty()) "text/plain" else {
            val extensions = uris.map { it.lastPathSegment?.substringAfterLast('.')?.lowercase() }.distinct()
            when (extensions.singleOrNull()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/*" }
        }
        putExtra(Intent.EXTRA_TEXT,text)
        putExtra(Intent.EXTRA_SUBJECT,"คำสั่งจาก DIRECTOR X")
        putExtra(Intent.EXTRA_TITLE,"DIRECTOR X")
        clipData = ClipData.newPlainText("คำสั่ง DIRECTOR X",text).apply {
            uris.forEach { addItem(ClipData.Item(it)) }
        }
        if (uris.isNotEmpty()) {
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM,uris[0])
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Keep ChatGPT in its own Android task. Without NEW_TASK, some Android
        // launchers place the ChatGPT screen inside DIRECTOR X's Recents card,
        // so selecting DIRECTOR X only reopens ChatGPT.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** Mixed document and image transfer; the receiving AI still controls submission and execution. */
internal fun buildStoryPackageIntent(text: String, uris: ArrayList<Uri>): Intent {
    require(uris.size == 4) { "ต้องมีไฟล์คำสั่งและ MASTER 3 ภาพ" }
    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type="*/*"
        putExtra(Intent.EXTRA_TEXT,text)
        putExtra(Intent.EXTRA_SUBJECT,"DIRECTOR X • เขียนบทและส่งกลับ story.json")
        putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
        clipData=ClipData.newPlainText("คำสั่งวางบท",text).apply { uris.forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Carry the instruction as an actual attachment when Gemini ignores EXTRA_TEXT. */
internal fun buildGeminiPackageIntent(text: String, command: Uri, images: ArrayList<Uri>): Intent {
    require(images.isNotEmpty() && images.size<=9) { "ต้องมี MASTER 1–9 ภาพพร้อมไฟล์คำสั่ง" }
    val files=arrayListOf(command).apply { addAll(images) }
    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type="*/*"
        putExtra(Intent.EXTRA_TEXT,text)
        putExtra(Intent.EXTRA_SUBJECT,"สร้างภาพตามไฟล์ READ_FIRST_IMAGE_COMMAND.txt พร้อม MASTER ที่แนบ")
        putParcelableArrayListExtra(Intent.EXTRA_STREAM,files)
        clipData=ClipData.newPlainText("คำสั่งและ MASTER",text).apply { files.forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal const val GEMINI_IMAGE_REQUEST = "สร้างและแสดงภาพจริง 1 ภาพตามรายละเอียดต่อไปนี้ ใช้ภาพ MASTER ที่แนบจับคู่ตัวละครตามรหัสเต็มโดยไม่สนใจนามสกุล รักษาข้อความบนปกตามคำสั่ง หากเป็นภาพฉากให้ทำตามข้อกำหนดของฉาก ไม่ต้องสรุปคำสั่ง ไม่ต้องเขียนโค้ด XML หากสร้างภาพไม่ได้ให้แจ้งสาเหตุ"

internal fun geminiImageMessage(command: String, twoCovers: Boolean = false): String = (if(twoCovers) GEMINI_IMAGE_REQUEST.replace("1 ภาพ","2 ภาพแยกไฟล์") else GEMINI_IMAGE_REQUEST)+"\n\n"+command

internal fun imageProviderOptions(preferred: String): List<String> = listOf("ChatGPT", "Gemini").let { options ->
    if (preferred in options) listOf(preferred) + options.filter { it != preferred } else options
}

internal fun buildGeminiImageShareIntent(text: String, images: ArrayList<Uri>): Intent {
    require(images.isNotEmpty() && images.size<=9) { "ต้องมีภาพอ้างอิง 1–9 ภาพ" }
    return Intent(if(images.size==1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type="image/*"
        putExtra(Intent.EXTRA_TEXT,text)
        if(images.size==1) putExtra(Intent.EXTRA_STREAM,images[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,images)
        clipData=ClipData("คำสั่งและภาพอ้างอิง", arrayOf("image/*", "text/plain"), ClipData.Item(text, null, null, images[0])).apply { images.drop(1).forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal fun neutralIdentityGuide(source: String, aspect: String): String =
    ImagePromptPolicy.identity(source) + "\n" + ImagePromptPolicy.style(aspect) + " ไม่มีข้อความหรือลายน้ำ หากสร้างไม่ได้ให้แจ้งตามจริง"

/** Whole-story workspace. Legacy preferences and character masters stay untouched. */
class StudioActivity : AppCompatActivity() {
    private val storyUiHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var geminiStatusView: TextView?=null
    private var geminiProgressView: ProgressBar?=null
    private var geminiLiveDialog: TextView?=null
    private val storyPoll=object: Runnable { override fun run() {
        if(!isFinishing) {
            val status=readGeminiStatus()
            geminiStatusView?.text=geminiStatusText(status)
            geminiProgressView?.progress=status?.optInt("percent")?.coerceIn(0,100) ?: 0
            geminiLiveDialog?.text=geminiStatusText(status,true)+if(status?.optString("state")=="running" && GeminiStoryService.running) "\n\nระบบจะทำต่อเองระหว่างรอ • กดหยุดเมื่อต้องการพักงาน" else ""
            if(plan==null && File(project,"story.json").isFile && !importing) render()
            storyUiHandler.postDelayed(this,1000)
        }
    } }
    private val characterMemory by lazy {
        CharacterMemoryStore(File(filesDir,"character_memory"), JSONObject(assets.open("character_profiles.json").bufferedReader().use { it.readText() }))
    }
    private val detailedImageProfiles get() = prefs.getBoolean("detailed_image_profiles",false)
    private fun promptProfile(id: String, source: File): String =
        if(detailedImageProfiles) characterMemory.brief(id,source) else ""
    private fun memoryId(person: JSONObject): String = FlowCast.id(person) ?: person.optString("master").ifBlank { person.optString("name") }
    private fun memoryBrief(person: JSONObject): String {
        val profile=characterMemory.read(memoryId(person),masterFor(person))
        return ((listOf("face","hair") + BodyReferencePolicy.fields.map { it.first }).map { profile.optString(it) } +
            if(person.has("accessories")) person.optString("accessories") else profile.optString("accessories"))
            .filter { it.isNotBlank() }.joinToString("; ")
    }
    private val base get() = File(filesDir, "studio").apply { mkdirs() }
    private val prefs get() = getSharedPreferences("studio", MODE_PRIVATE)
    private val project get() = File(base, prefs.getString("project", "draft")!!).apply { mkdirs() }
    private val pendingRecovery get() = File(filesDir, "pending_story_import")
    private var plan: JSONObject? = null
    private var importing = false
    private var activeProductionDialog: AlertDialog? = null
    private var pendingStoryShare: (() -> Unit)? = null
    private var pendingStorySummary = ""
    private var pendingReceipt: (() -> Unit)? = null
    private var exportEpisode: Int?
        get() = prefs.getInt("export_episode",0).takeIf { it>0 }
        set(value) { prefs.edit().putInt("export_episode",value ?: 0).apply() }
    private var backupMode: Boolean
        get() = prefs.getBoolean("backup_mode",false)
        set(value) { prefs.edit().putBoolean("backup_mode",value).apply() }
    private val updater = StudioUpdater(this)
    private lateinit var root: LinearLayout
    private val navy = Color.rgb(18,29,49)
    private val gold = Color.rgb(232,180,82)
    private val electricBlue = Color.rgb(30,156,255)
    private val cyan = Color.rgb(42,220,255)
    private val ice = Color.rgb(248,246,239)
    private val muted = Color.rgb(164,164,169)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun backdrop(top: Int, bottom: Int, border: Int? = null) = GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(top,bottom)).apply {
        cornerRadius = dp(16).toFloat()
        if (border != null) setStroke(dp(1),border)
    }
    private val menuColors = arrayOf(
        intArrayOf(Color.rgb(255,218,112),Color.rgb(255,177,58)),
        intArrayOf(Color.rgb(119,232,250),Color.rgb(63,184,235)),
        intArrayOf(Color.rgb(219,195,255),Color.rgb(176,143,244)),
        intArrayOf(Color.rgb(128,240,202),Color.rgb(63,200,160)),
        intArrayOf(Color.rgb(255,185,209),Color.rgb(245,139,175)))
    private fun menuIcon(index: Int) = listOf("🎭", "🎨", "📚", "🎬", "💡")[index % 5]
    private fun choiceMenu(title: String, choices: List<String>, cancel: String = "✖️ ยกเลิก",
        cancelable: Boolean = true, onCancel: (() -> Unit)? = null, action: (Int) -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(18),dp(16),dp(12))
            background = backdrop(Color.rgb(24,24,27),navy,Color.rgb(74,61,39))
        }
        panel.addView(TextView(this).apply {
            text = title; textSize = 20f; setTextColor(gold); setTypeface(null,Typeface.BOLD)
            setPadding(0,0,0,dp(14))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        panel.addView(scroll,LinearLayout.LayoutParams(-1,-2))
        val dialog = AlertDialog.Builder(this).setView(panel).setCancelable(cancelable).create()
        choices.forEachIndexed { index, value ->
            list.addView(TextView(this).apply {
                text = value; textSize = 16f; setTextColor(navy); setTypeface(null,Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL; minHeight = dp(60)
                setPadding(dp(14),dp(14),dp(14),dp(14))
                background = backdrop(Color.rgb(248,246,239),Color.rgb(226,234,243),Color.rgb(173,187,204))
                layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(10) }
                isClickable = true; isFocusable = true
                val attr = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground,attr,true)
                foreground = getDrawable(attr.resourceId)
                setOnClickListener { dialog.dismiss(); action(index) }
            })
        }
        if (choices.isEmpty()) list.addView(TextView(this).apply {
            text = "📭 ยังไม่มีรายการ"; textSize = 16f; setTextColor(ice); setPadding(0,dp(12),0,dp(12))
        })
        panel.addView(TextView(this).apply {
            text = cancel; textSize = 16f; setTextColor(ice); gravity = Gravity.CENTER
            minHeight = dp(48); background = backdrop(Color.rgb(36,36,40),Color.rgb(28,28,32))
            isFocusable = true; setOnClickListener { dialog.dismiss(); onCancel?.invoke() }
        })
        dialog.setOnCancelListener { onCancel?.invoke() }
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(resources.displayMetrics.widthPixels - dp(24),-2)
        panel.post {
            val maximum = (resources.displayMetrics.heightPixels * 0.85f).toInt()
            val available = maximum - (panel.height - scroll.height)
            if (scroll.height > available) scroll.layoutParams = LinearLayout.LayoutParams(-1,available.coerceAtLeast(dp(80)))
        }
    }
    private val legacyCategories = listOf(
        "The Broken Vows | บันทึกรอยร้าวชีวิตคู่",
        "Sweet Revenge | เกมรักซ่อนลวง บทเรียนรสแซ่บ",
        "Family Secrets | ความลับใต้ชายคา...ที่ไม่มีใครกล้าพูด",
        "Twisted Truth | พล็อตหักมุม...ความจริงใต้เตียง",
        "Healing Hearts | ซ่อมรักหลังบ้าน ซีรีส์ฮีลใจ")
    private val categories = ChannelCatalog.mainCategories.map { it.label }
    private var masterSlot: String?
        get() = prefs.getString("master_slot", null)
        set(value) { prefs.edit().putString("master_slot", value).apply() }
    private val masterPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = masterSlot
        if (uri != null && slot in listOf("gawin", "rinlada", "mind")) {
            try {
                val folder = File(filesDir, "character_masters").apply { mkdirs() }
                val temporary = File(folder, "$slot.tmp")
                contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(temporary.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ไฟล์นี้ไม่ใช่ภาพที่อ่านได้" }
                val previous=File(folder,"$slot.master"); if(previous.exists()) previous.copyTo(File(folder,"$slot.previous"),overwrite=true)
                require(temporary.renameTo(previous)) { "บันทึกภาพไม่ได้" }
                notice("บันทึกภาพแล้ว เปิดเมนูตรวจภาพ MASTER เพื่อเทียบกับต้นฉบับก่อนใช้งาน")
            } catch (e: Exception) { notice("รับภาพไม่สำเร็จ: ${e.message}") }
        }
        masterSlot = null
    }
    // Body references are not actor identities. New actors are registered only after image review.
    private val bodyTemplateFiles=listOf("body_01.png")
    private val actorCatalogFile get() = File(filesDir,"actor_catalog_v2.json")
    private fun actorCatalog(): JSONArray = if(!actorCatalogFile.exists()) JSONArray() else JSONObject(actorCatalogFile.readText()).getJSONArray("records")
    // Include the bundled legacy references and user-created actors without changing either catalog.
    private fun castingCatalog(): JSONArray {
        val legacy=JSONObject(assets.open("master_library/catalog.json").bufferedReader().use { it.readText() }).getJSONArray("records")
        val custom=actorCatalog()
        val result=JSONArray()
        val ids=mutableSetOf<String>()
        for (rows in listOf(legacy,custom)) for (i in 0 until rows.length()) {
            val row=JSONObject(rows.getJSONObject(i).toString())
            require(ids.add(row.getString("id"))) { "รหัส MASTER ซ้ำในคลัง: ${row.getString("id")}" }
            result.put(row)
        }
        return result
    }
    private fun castingSource(row: JSONObject): File {
        val entry=masterLibrary.entries().firstOrNull { it.id==row.getString("id") }
        return if(entry!=null) masterLibrary.source(entry) else File(filesDir,"actor_master_v2/"+row.getString("file"))
    }
    private fun bodyTemplateFile(index: Int): File {
        val target=File(cacheDir,"body_template_sources/${bodyTemplateFiles[index]}")
        target.parentFile!!.mkdirs()
        assets.open("body_templates/"+bodyTemplateFiles[index]).use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }
    private val actorImagePicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pending=prefs.getString("new_actor_pending",null)
        if(uri!=null && pending!=null) runCatching {
            val person=JSONObject(pending)
            val input=contentResolver.openInputStream(uri) ?: error("เปิดภาพไม่ได้")
            val bytes=input.use { it.readBytes() }
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            require(bounds.outWidth>0 && bounds.outHeight>0) { "ไฟล์ไม่ใช่ภาพ" }
            val extension=when(bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; "image/png" -> "png"; else -> error("รองรับภาพ PNG, JPG หรือ WebP") }
            val records=actorCatalog()
            require((0 until records.length()).none { records.getJSONObject(it).getString("display_name")==person.getString("display_name") }) { "ชื่อนี้มีในคลังแล้ว" }
            val fileName=person.getString("id") + "." + extension
            val dir=File(filesDir,"actor_master_v2").apply { mkdirs() }
            val target=File(dir,fileName)
            require(!target.exists()) { "รหัสตัวละครซ้ำ กรุณาเริ่มใหม่" }
            val temp=File(dir,fileName+".pending")
            try {
                temp.writeBytes(bytes)
                person.put("file",fileName).put("original_filename",fileName).put("sha256",MasterLibrary.digest(bytes)).put("age_basis","user_assigned")
                records.put(person)
                check(temp.renameTo(target)) { "บันทึกภาพไม่ได้" }
                try {
                    val catalogTemp=File(filesDir,"actor_catalog_v2.pending")
                    catalogTemp.writeText(JSONObject().put("records",records).toString(2))
                    check(catalogTemp.renameTo(actorCatalogFile)) { "บันทึกทะเบียนไม่ได้" }
                } catch(e: Exception) { target.delete(); throw e }
            } finally { temp.delete() }
            prefs.edit().remove("new_actor_pending").apply()
        }.onSuccess { notice("บันทึก MASTER ตัวละครใหม่แล้ว • ตรวจภาพและข้อมูลในคลังได้") }.onFailure { notice(it.message ?: "บันทึกตัวละครไม่ได้") }
    }
    private fun bodyTemplateMenu() {
        choiceMenu("แม่แบบรูปร่าง",listOf("แม่แบบ 1 • ผู้ชาย")) { index ->
            val file=runCatching { bodyTemplateFile(index) }.getOrElse { notice("เปิดแม่แบบไม่ได้"); return@choiceMenu }
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
            panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(240)) })
            val name=EditText(this).apply { hint="ชื่อนักแสดงใหม่ (ชื่อถาวร)"; panel.addView(this) }
            val age=EditText(this).apply { hint="อายุสมมติ 25–90 ปี"; inputType=android.text.InputType.TYPE_CLASS_NUMBER; panel.addView(this) }
            val face=EditText(this).apply { hint="ลักษณะใบหน้าและทรงผมใหม่ (ไทยหรืออังกฤษ)"; panel.addView(this) }
            val styling=EditText(this).apply { hint="สีผิวและเสื้อผ้า (ไทยหรืออังกฤษ)"; panel.addView(this) }
            panel.addView(TextView(this).apply { text="1. ส่งคำสั่งพร้อมแม่แบบไปสร้างใน ChatGPT หรือ Gemini\n2. ตรวจภาพจริง แล้วกลับมาเลือก นำเข้าภาพเพื่อบันทึก" })
            val dialog=AlertDialog.Builder(this).setTitle("สร้างตัวละครจากแม่แบบ ${index+1}").setView(ScrollView(this).apply { addView(panel) })
                .setPositiveButton("ส่งคำสั่งสร้างภาพ",null).setNeutralButton("นำเข้าภาพที่ตรวจแล้ว",null).setNegativeButton("ปิด",null).showDirector()
            fun profile(): JSONObject? {
                val n=name.text.toString().trim(); val years=age.text.toString().toIntOrNull(); val appearance=face.text.toString().trim()
                if(n.isEmpty() || years==null || years !in 25..90 || appearance.isEmpty()) { notice("กรอกชื่อ อายุ 25–90 ปี และลักษณะใบหน้า/ทรงผม"); return null }
                if((0 until actorCatalog().length()).any { actorCatalog().getJSONObject(it).optString("display_name")==n }) { notice("ชื่อนี้มีในคลังแล้ว"); return null }
                val id="CHAR_"+(System.currentTimeMillis()%2000000000L).toString()
                return JSONObject().put("id",id).put("display_name",n).put("character_age",years).put("character_gender",if(index==0) "man" else "woman").put("casting_description",appearance).put("body_template",index+1)
            }
            dialog.getButton(-1).setOnClickListener {
                val p=profile() ?: return@setOnClickListener
                val gender=p.getString("character_gender")
                val appearance=p.getString("casting_description")
                val style=styling.text.toString().trim().ifBlank { "natural skin tone and elegant opaque clothing" }
                val command=ImagePromptPolicy.threeViewCharacter("a fictional adult $gender aged ${p.getInt("character_age")} years, new face and hairstyle: $appearance, $style, body silhouette and proportions based on the attached reference image")
                shareImageCommand(command,listOf("BODY_TEMPLATE_${index+1}" to file),bodyOnly=true)
            }
            dialog.getButton(-3).setOnClickListener {
                val p=profile() ?: return@setOnClickListener
                prefs.edit().putString("new_actor_pending",p.toString()).apply()
                actorImagePicker.launch(arrayOf("image/*"))
            }
        }
    }
    private fun actorCatalogMenu() {
        runCatching {
            val records=actorCatalog()
            choiceMenu("คลังตัวละครใหม่", listOf("➕ สร้างตัวละครจากแม่แบบ","📚 ดู MASTER คลังเดิม")+(0 until records.length()).map { i -> records.getJSONObject(i).let { "${it.getString("display_name")} • อายุ ${it.getInt("character_age")} ปี" } }) { index ->
                if(index==0) bodyTemplateMenu() else if(index==1) libraryMenu() else {
                    val row=records.getJSONObject(index-2)
                    val file=File(filesDir,"actor_master_v2/"+row.getString("file"))
                    choiceMenu(row.getString("display_name"),listOf("ดู MASTER","นำออกจากคลังเรื่องใหม่")) { action ->
                        if(action==0) { if(file.isFile) fullImage(file,row.getString("display_name")) else notice("ไม่พบภาพ MASTER") }
                        else {
                            val rest=JSONArray(); for(i in 0 until records.length()) if(i!=index-2) rest.put(records.getJSONObject(i))
                            val temp=File(filesDir,"actor_catalog_v2.pending")
                            temp.writeText(JSONObject().put("records",rest).toString(2))
                            if(temp.renameTo(actorCatalogFile)) notice("นำออกจากคลังแล้ว • เรื่องที่สร้างไว้ยังใช้ภาพเดิม") else notice("บันทึกคลังไม่ได้")
                        }
                    }
                }
            }
        }.onFailure { notice("อ่านคลังไม่ได้: ${it.message}") }
    }
    private val masterLibrary by lazy { MasterLibrary(File(filesDir,"master_library")) { assets.open(it).use { stream -> stream.readBytes() } } }
    private fun libraryMenu() {
        runCatching {
            val entries=masterLibrary.entries()
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
            panel.addView(TextView(this).apply { text=if(entries.isEmpty()) "คลังว่าง • เพิ่มภาพใหม่ผ่านเมนูตัวละคร" else "${entries.size} แผ่น • หนึ่งแผ่นต่อหนึ่งตัวละคร" })
            val search=EditText(this).apply { hint="ค้นหา CHAR_001 หรือชื่อไฟล์"; isSingleLine=true; panel.addView(this) }
            val list=LinearLayout(this).apply { orientation=1 }
            panel.addView(ScrollView(this).apply { addView(list) },LinearLayout.LayoutParams(-1,dp(420)))
            fun show(query: String) {
                list.removeAllViews()
                entries.filter { (it.id+" "+it.displayName+" "+it.original).contains(query,ignoreCase=true) }.forEach { entry ->
                    list.addView(android.widget.Button(this).apply {
                        text="${entry.id} • ${entry.displayName} • อายุสมมติ ${entry.characterAge} ปี • ${entry.original}"
                        setOnClickListener { libraryEntry(entry) }
                    })
                }
            }
            search.addTextChangedListener(object: android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { show(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            show("")
            AlertDialog.Builder(this).setTitle("🎭 คลัง MASTER").setView(panel).setNegativeButton("ปิด",null).showDirector()
        }.onFailure { notice("เปิดคลังไม่ได้: ${it.message}") }
    }
    private fun libraryEntry(entry: MasterLibrary.Entry) {
        runCatching {
            val file=masterLibrary.source(entry)
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(8),dp(12),dp(8)) }
            panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(400)); setOnClickListener { fullImage(file,entry.id) } })
            panel.addView(TextView(this).apply { text="${entry.id} • ${entry.displayName} • อายุสมมติ ${entry.characterAge} ปี • ${entry.original}\nภาพต้นฉบับ 3 มุมมอง แตะภาพเพื่อขยาย" })
            AlertDialog.Builder(this).setTitle("${entry.id} • ${entry.displayName}").setView(ScrollView(this).apply { addView(panel) })
                .setPositiveButton("ใช้กับเรื่องใหม่") { _,_ ->
                    choiceMenu("กำหนดภาพ ${entry.id}",listOf("ตัวละครหลัก: กวิน","ตัวละครหลัก: รินรดา","ตัวละครหลัก: เมย์","เพิ่มตัวละครเสริมพร้อมตั้งชื่อ")) { slot ->
                        if(slot==3) addLibrarySupport(entry) else runCatching {
                            val id=listOf("gawin","rinlada","mind")[slot]
                            masterLibrary.assign(entry,File(filesDir,"character_masters/$id.master"))
                            notice("เลือก ${entry.id} สำหรับเรื่องใหม่แล้ว • เรื่องเดิมใช้ภาพเดิม")
                        }.onFailure { notice(it.message ?: "บันทึกไม่ได้") }
                    }
                }.setNegativeButton("ปิด",null).showDirector()
        }.onFailure { notice(it.message ?: "เปิดภาพไม่ได้") }
    }
    private fun addLibrarySupport(entry: MasterLibrary.Entry) {
        if(readSupportingCast().length()>=8) { notice("เลือกตัวละครเสริมได้ไม่เกิน 8 คนต่อเรื่อง • นำคนเดิมออกก่อน"); return }
        val input=EditText(this).apply { hint="ชื่อตัวละครในเรื่อง"; setText(entry.displayName) }
        AlertDialog.Builder(this).setTitle("เพิ่ม ${entry.id} สำหรับเรื่องใหม่").setView(input).setPositiveButton("เพิ่ม") { _,_ ->
            runCatching {
                val name=input.text.toString().trim()
                val roster=readSupportingCast()
                require(name.isNotBlank() && StoryIdentity.keyFor(name)==null && (0 until roster.length()).none { roster.getJSONObject(it).getString("name")==name }) { "ระบุชื่อไม่ซ้ำกับตัวละครที่มีอยู่" }
                require((0 until roster.length()).none { roster.getJSONObject(it).optString("library_id")==entry.id }) { "เลือก MASTER นี้ไว้แล้ว" }
                val id="support_"+UUID.randomUUID().toString().take(8)
                val person=JSONObject().put("name",name).put("master","${id.uppercase()}_MASTER.png")
                    .put("library_id",entry.id).put("visual_description",MasterLibrary.description(entry.id))
                roster.put(person)
                require(FlowCast.issues(JSONObject().put("supporting_characters",roster)).isEmpty()) { "ข้อมูลตัวละครไม่ถูกต้อง" }
                masterLibrary.assign(entry,File(filesDir,"supporting_masters/$id.master"))
                // Keep the existing array format consumed by legacy projects and UI.
                val target=File(filesDir,"supporting_cast.json")
                val pending=File(filesDir,"supporting_cast.json.pending")
                pending.writeText(roster.toString(2)); check(pending.renameTo(target))
                notice("เพิ่ม $name จาก ${entry.id} แล้ว • ใช้เมื่อสร้างเรื่องใหม่")
            }.onFailure { notice(it.message ?: "เพิ่มไม่ได้") }
        }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun selectedLeadReferences(): JSONObject {
        val entries=masterLibrary.entries()
        val selected=JSONObject()
        FlowProductionPack.names.keys.forEach { id ->
            val file=File(filesDir,"character_masters/$id.master")
            if(file.isFile) {
                val hash=MasterLibrary.digest(file.readBytes())
                entries.firstOrNull { it.sha256==hash }?.let { selected.put(id,JSONObject().put("library_id",it.id).put("visual_description",MasterLibrary.description(it.id))) }
            }
        }
        return selected
    }

    private fun characterMasters() {
        val names=listOf("gawin" to "กวิน", "rinlada" to "รินรดา", "mind" to "เมย์")
        choiceMenu("🎭 MASTER และความจำตัวละคร",names.map { (id,name) -> val file=File(filesDir,"character_masters/$id.master"); "$name • ${if(characterMemory.read(id,file).optLong("reference_reviewed_at")>0) "ตรวจภาพแล้ว" else "ยังไม่ได้ตรวจภาพ"}" } + listOf("🎭 คลัง MASTER")) { i ->
            if (i == names.size) { libraryMenu() } else {
                val (id,name)=names[i]; val file=File(filesDir,"character_masters/$id.master")
                choiceMenu(name,listOf("ดู MASTER","เปลี่ยนภาพ MASTER","ข้อมูลใบหน้า ผม และรูปร่าง","เตรียม MASTER มาตรฐาน","ตรวจภาพ MASTER")) { action -> when(action) {
                    0 -> if(file.exists()) fullImage(file,name) else notice("ยังไม่มี MASTER")
                    1 -> { masterSlot=id; masterPicker.launch(arrayOf("image/*")) }
                    2 -> editCharacterMemory(id,name,file)
                    3 -> sendProductionMaster(id,name,file)
                    4 -> reviewMaster(id,name,file)
                } }
            }
        }
    }
    private fun sendProductionMaster(id: String, name: String, file: File) {
        if(!file.exists()) { notice("เพิ่ม MASTER ก่อน"); return }
        val source=masterExportName(file,false) ?: run { notice("อ่านภาพ MASTER ไม่ได้"); return }
        val command=ImagePromptPolicy.productionMaster(name,source,promptProfile(id,file))
        showFlowCode("คำสั่งเตรียม MASTER ใน Flow",command,"${id.uppercase()}_MASTER_COMMAND.txt")
    }
    private fun reviewMaster(id: String, name: String, file: File) {
        if(!file.exists()) { notice("ยังไม่มี MASTER"); return }
        val profile=characterMemory.read(id,file)
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(320)) })
        panel.addView(TextView(this).apply { text="ตรวจเทียบต้นฉบับ: ใบหน้าและผมตรงรายคน • รูปร่างไม่เปลี่ยนโดยไม่ตั้งใจ • มือปกติ • ชุดพื้นฐานชัดเจน • แสงและภาพคมชัด\nการยืนยันนี้เป็นผลตรวจของผู้ใช้ ไม่ใช่ผลรับรองจากระบบสร้างภาพ" })
        AlertDialog.Builder(this).setTitle("ตรวจ MASTER • $name").setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("ตรวจแล้ว ใช้ภาพนี้") { _,_ -> profile.put("reference_reviewed_at",System.currentTimeMillis()); characterMemory.save(id,file,profile); notice("บันทึกผลตรวจของภาพนี้แล้ว") }
            .setNeutralButton("เลือกภาพใหม่") { _,_ -> if(file.parentFile?.name=="character_masters") { masterSlot=id; masterPicker.launch(arrayOf("image/*")) } else { castTarget=name to file; castPicker.launch(arrayOf("image/*")) } }
            .setNegativeButton("ปิด",null).showDirector()
    }
    private fun editCharacterMemory(id: String, name: String, file: File) {
        if (!file.exists()) { notice("เพิ่มภาพ MASTER ก่อนบันทึกโปรไฟล์"); return }
        val profile=characterMemory.read(id,file)
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        panel.addView(TextView(this).apply { text="ข้อมูลนี้ผูกกับภาพ MASTER นี้เท่านั้น • บันทึกเฉพาะสิ่งที่เห็น ชุดและฉากกำหนดในบท" })
        panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(240)) })
        val fields=listOf("face" to "ใบหน้าและดวงตา", "hair" to "ผมและหนวดเครา") + BodyReferencePolicy.fields + ("accessories" to "เครื่องประดับอ้างอิง (เปลี่ยนตามบทได้)")
        val inputs=fields.map { (key,label) -> key to EditText(this).apply { hint=label; setText(profile.optString(key)); panel.addView(this) } }
        AlertDialog.Builder(this).setTitle("ความจำตัวละคร • $name").setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("บันทึก") { _,_ -> inputs.forEach { (key,input) -> profile.put(key,input.text.toString().trim()) }; characterMemory.save(id,file,profile); notice("บันทึกข้อมูลอ้างอิงของ $name แล้ว") }
            .setNegativeButton("ยกเลิก",null).showDirector()
    }
    private val otherGenres = listOf("ดราม่าชีวิต", "ครอบครัว", "โรแมนติก", "ตลก", "สืบสวน", "ระทึกขวัญ", "สยองขวัญ", "แฟนตาซี", "แอ็กชัน", "อบอุ่นหัวใจ")
    private val smartDirector = "ระบบกำกับละครอัจฉริยะ"
    private val genreLabels = listOf(
        "🎭 ดราม่าชีวิต\nต่อสู้ชีวิตและการเริ่มต้นใหม่",
        "🏡 ครอบครัว\nความรักและความผูกพันในบ้าน",
        "💕 โรแมนติก\nเรื่องรักและความสัมพันธ์",
        "😂 ตลก\nเรื่องสนุก ชวนยิ้มและหัวเราะ",
        "🔎 สืบสวน\nตามเบาะแส เปิดเผยความจริง",
        "😱 ระทึกขวัญ\nลุ้นทุกฉาก ปริศนาและอันตราย",
        "👻 สยองขวัญ\nเรื่องลี้ลับและความน่ากลัว",
        "✨ แฟนตาซี\nโลกจินตนาการและพลังวิเศษ",
        "🔥 แอ็กชัน\nภารกิจ การไล่ล่าและการต่อสู้",
        "🌷 อบอุ่นหัวใจ\nความหวัง การช่วยเหลือและกำลังใจ")
    private val originalChannel = ChannelCatalog.channel
    private val planPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) background {
            val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val candidate = JSONObject(text.removePrefix("\uFEFF"))
            validate(candidate)
            val id = UUID.randomUUID().toString()
            val folder = File(base, id).apply { mkdirs() }
            File(folder, "story.json").writeText(candidate.toString(2))
            prefs.edit().putString("project", id).commit()
        }
    }
    private val packagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) receivePackage(uris)
    }
    private fun receivePackage(selected: List<Uri>, confirmed: Boolean = false) {
        importing = true
        val dialog = AlertDialog.Builder(this).setMessage("กำลังตรวจบทและภาพ…").setCancelable(false).showDirector()
        Thread {
            try {
                File(cacheDir,"story_import").deleteRecursively()
                val staging = File(cacheDir, "story_import/${UUID.randomUUID()}").apply { mkdirs() }
                val files = mutableListOf<Uri>()
                val failedZipEntries = mutableListOf<String>()
                for (uri in selected) {
                    if (displayName(uri).endsWith(".zip", true) || contentResolver.getType(uri) in listOf("application/zip", "application/x-zip-compressed")) {
                        val archive = File(staging, "archive-${files.size}.zip")
                        contentResolver.openInputStream(uri)!!.use { input -> archive.outputStream().use { input.copyTo(it) } }
                        require(archive.length() > 4 && archive.inputStream().use { it.read() == 'P'.code && it.read() == 'K'.code }) { "ไฟล์ที่เลือกไม่ใช่ ZIP ที่สมบูรณ์" }
                        val unpacked = StoryFiles.unpackFile(archive, File(staging, "unpacked-${files.size}"))
                        files.addAll(unpacked.files.map { Uri.fromFile(it) })
                        failedZipEntries.addAll(unpacked.failedEntries)
                    } else files.add(uri)
                }
                // A broken large ZIP may end before story.json. Keep its valid images,
                // then automatically merge them when the separately downloaded story.json arrives.
                val selectedHasStory = files.any { displayName(it).equals("story.json", true) }
                if (selectedHasStory && pendingRecovery.isDirectory) {
                    files.addAll(pendingRecovery.listFiles()?.filter { it.isFile && it.name != "recovery.txt" }?.map { Uri.fromFile(it) }.orEmpty())
                }
                val scripts = files.filter { displayName(it).equals("story.json", true) }
                require(scripts.size <= 1) { "เลือก story.json ของเรื่องเดียวเท่านั้น" }
                val videos = files.filter { displayName(it).endsWith(".mp4",true) || contentResolver.getType(it)=="video/mp4" }
                val pictures = files.filter { displayName(it).substringAfterLast('.').lowercase() in listOf("png", "jpg", "jpeg", "webp") || contentResolver.getType(it)?.startsWith("image/") == true }
                val masters = pictures.filter { Regex("^CHARACTER_\\d+\\.", RegexOption.IGNORE_CASE).containsMatchIn(displayName(it)) }
                val covers = pictures.filter { StoryFiles.coverName(displayName(it)) != null }
                val scenes = pictures.filter { !StoryFiles.isMaster(displayName(it)) } - covers.toSet()
                require(scripts.isNotEmpty() || pictures.isNotEmpty() || videos.isNotEmpty()) { "ไม่พบ story.json หรือภาพในไฟล์ที่เลือก กรุณาเลือก ZIP ชุดงาน หรือ JSON/PNG/JPG" }
                val candidate = scripts.firstOrNull()?.let { contentResolver.openInputStream(it)!!.bufferedReader().use { reader -> JSONObject(reader.readText().removePrefix("\uFEFF")) } }
                if (candidate == null && plan == null && pictures.isNotEmpty()) {
                    pendingRecovery.deleteRecursively(); pendingRecovery.mkdirs()
                    pictures.forEachIndexed { index, uri ->
                        val safeName = displayName(uri).ifBlank { "recovered-$index.img" }
                        contentResolver.openInputStream(uri)!!.use { input -> File(pendingRecovery,safeName).outputStream().use { input.copyTo(it) } }
                    }
                    File(pendingRecovery,"recovery.txt").writeText("กู้ภาพ ${pictures.size} ไฟล์\n" + failedZipEntries.joinToString("\n"))
                    runOnUiThread {
                        importing=false; dialog.dismiss(); render()
                        notice("กู้ภาพที่สมบูรณ์ไว้แล้ว ${pictures.size} ไฟล์ • ZIP ขาด story.json\n\nนำเข้า story.json ผ่านเมนูนำเข้าบทอีกครั้ง แอปจะรวมภาพที่กู้ไว้ให้อัตโนมัติ ไม่ต้องเลือก ZIP ซ้ำ")
                    }
                    return@Thread
                }
                val destinationStory = candidate ?: plan ?: error("ยังไม่มีบท กรุณานำเข้า story.json ก่อน")
                val checkedVideos = videos.map { uri ->
                    val number=StoryFiles.sceneNumber(displayName(uri)) ?: error("วิดีโอต้องชื่อ EP01_SC001.mp4: ${displayName(uri)}")
                    val scene=rows(destinationStory).firstOrNull { it.getInt("ep")==number.first && it.getInt("scene")==number.second } ?: error("วิดีโอไม่ตรงกับบท: ${displayName(uri)}")
                    val temp=File(staging,"checked_videos/${key(scene)}.mp4").apply { parentFile!!.mkdirs() }
                    contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    val reader=android.media.MediaMetadataRetriever()
                    try { reader.setDataSource(temp.path); require(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it>0 }==true) { "วิดีโออ่านไม่ได้" } } finally { reader.release() }
                    key(scene) to temp
                }
                require(checkedVideos.map { it.first }.distinct().size==videos.size) { "มีวิดีโอฉากซ้ำ" }
                val checkedCovers = covers.map { uri ->
                    val name = StoryFiles.coverName(displayName(uri))!!
                    require(name in coverNames(destinationStory)) { "ปก $name ไม่ตรงกับ EP ในเรื่อง" }
                    val temp = File(staging,"checked/$name.img").apply { parentFile!!.mkdirs() }
                    contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(temp.path,bounds)
                    val ratio = if (name.endsWith("16x9")) 16.0 / 9 else 9.0 / 16
                    require(bounds.outHeight > 0 && kotlin.math.abs(bounds.outWidth.toDouble()/bounds.outHeight - ratio) < 0.04) { "$name ต้องมีสัดส่วน ${if (name.endsWith("16x9")) "16:9" else "9:16"}" }
                    name to temp
                }
                require(checkedCovers.map { it.first }.distinct().size == covers.size) { "มีไฟล์ปกซ้ำ กรุณาเลือกหนึ่งภาพต่อสัดส่วนต่อ EP" }
                if (candidate != null) validate(candidate)
                if (!confirmed) {
                    runOnUiThread {
                        importing=false; dialog.dismiss()
                        showPackagePreview(destinationStory, files, failedZipEntries) { receivePackage(selected,true) }
                    }
                    return@Thread
                }
                if (candidate != null) {
                    validate(candidate)
                    // Reimporting the same story must retain images already received.
                    val sameStory = plan?.let { old -> old.optString("title") == candidate.optString("title") && old.optString("category") == candidate.optString("category") && rows(old).map { it.toString() } == rows(candidate).map { it.toString() } } == true
                    val folder = if (sameStory) project else File(base, UUID.randomUUID().toString()).apply { mkdirs() }
                    importOriginalReferences(pictures,folder,candidate)
                    importNewMasters(masters, folder, candidate)
                    if (candidate.optString("character_mode","original") == "original") {
                        listOf("gawin","rinlada","mind").forEach { id ->
                            val target=File(folder,"original_masters/$id.master")
                            val source=File(filesDir,"character_masters/$id.master")
                            if(!target.exists() && source.exists()) { target.parentFile!!.mkdirs(); source.copyTo(target) }
                        }
                    }
                    val storyFile = File(folder,"story.json")
                    storyFile.writeText(candidate.t…58906 tokens truncated…     val target = File(folder,if(kind == "video") "videos/$id.mp4" else "images/$id.img")
            fun accept() { background {
                target.parentFile!!.mkdirs(); val temp = File(target.parentFile,target.name+".pending")
                try {
                    contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    require(temp.length() > 0) { "ไฟล์ว่าง" }
                    if (kind == "image") {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                        android.graphics.BitmapFactory.decodeFile(temp.path,bounds); require(bounds.outWidth > 0) { "อ่านภาพไม่ได้" }
                    } else {
                        val retriever = android.media.MediaMetadataRetriever()
                        try { retriever.setDataSource(temp.path); require(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it > 0 } == true) { "อ่านวิดีโอไม่ได้" } } finally { retriever.release() }
                    }
                    if (target.exists()) archiveVersion(target,folder,id,kind)
                    check(temp.renameTo(target)) { "บันทึกไม่ได้" }
                    val storyFile = File(folder,"story.json")
                    val story = JSONObject(storyFile.readText()); rows(story).firstOrNull { key(it) == id }?.put("user_review","pending")
                    storyFile.writeText(story.toString(2))
                } finally { temp.delete() }
            } }
            AlertDialog.Builder(this).setTitle("ตรวจไฟล์ก่อนนำเข้า").setMessage("${displayName(uri)} → $id\nชนิด: $kind\n${if(target.exists()) "ไฟล์เดิมจะเก็บเป็นเวอร์ชันก่อนแทน" else "เพิ่มไฟล์ใหม่"}")
                .setPositiveButton("นำเข้า") { _,_ -> accept() }.setNegativeButton("ยกเลิก",null).showDirector()
        }
        mediaSlot = null
    }
    private fun playVideo(s: JSONObject) {
        val file = video(s); if (!file.exists()) return notice("ยังไม่มีวิดีโอฉากนี้ • สร้างใน Flow แล้วนำเข้ากลับ")
        val view = VideoView(this).apply {
            setVideoPath(file.path); val controls = MediaController(this@StudioActivity); controls.setAnchorView(this); setMediaController(controls)
            setOnPreparedListener { start() }; setOnErrorListener { _,_,_ -> notice("เล่นไฟล์นี้ไม่ได้ กรุณานำเข้าวิดีโอ MP4 ที่รองรับ"); true }
        }
        val dialog = AlertDialog.Builder(this).setTitle(key(s)).setView(view).setPositiveButton("ปิด",null).create()
        dialog.setOnDismissListener { view.stopPlayback() }; dialog.show(); dialog.window?.setLayout(-1,dp(560))
    }
    private fun versionsMenu(s: JSONObject) {
        choiceMenu("เวอร์ชัน ${key(s)}",listOf("ภาพ", "วิดีโอ")) { choice ->
            val kind = if(choice == 0) "image" else "video"
            val files = File(project,"versions/${key(s)}/$kind").listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
            choiceMenu("เลือกเวอร์ชัน $kind",files.map { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified())) }) { i ->
                val source = files[i]; val target = if(kind == "image") image(s) else video(s)
                AlertDialog.Builder(this).setMessage("ใช้เวอร์ชันนี้? เวอร์ชันปัจจุบันจะเก็บไว้")
                    .setPositiveButton("ใช้เวอร์ชันนี้") { _,_ ->
                        if(target.exists()) archiveVersion(target,project,key(s),kind)
                        target.parentFile!!.mkdirs(); source.copyTo(target,overwrite=true); s.put("user_review","pending"); saveStory(); render()
                    }.setNegativeButton("ยกเลิก",null).showDirector()
            }
        }
    }
    private fun auditStory() {
        val p = plan ?: return notice("ยังไม่มีบท")
        val report = rows(p).map { s ->
            val cast = s.optJSONArray("characters") ?: JSONArray()
            val absent = (0 until cast.length()).map { cast.getJSONObject(it) }.filter { !masterFor(it).exists() }.map { it.optString("name") }
            "${key(s)} • ภาพ ${if(image(s).exists()) "✓" else "ขาด"} • วิดีโอ ${if(video(s).exists()) "✓" else "ขาด"} • ตรวจรับ ${s.optString("user_review","รอตรวจ")}\n" +
                (if(absent.isEmpty()) "" else "ขาด MASTER: ${absent.joinToString()}\n") +
                (if(s.optString("dialogue").length > 55) "ตรวจบทพูด: อาจยาวเกิน 8 วินาที\n" else "")
        }.joinToString("\n")
        val text = TextView(this).apply { this.text = report; setTextIsSelectable(true); setPadding(dp(16),dp(12),dp(16),dp(12)) }
        AlertDialog.Builder(this).setTitle("ตรวจงาน • ${p.optString("title")}").setView(ScrollView(this).apply { addView(text) }).setPositiveButton("ปิด",null).setNeutralButton("คัดลอก") { _,_ -> copyCommand(report) }.showDirector()
    }
    private fun storiesMenu() {
        val folders = base.listFiles()?.filter { File(it,"story.json").exists() || File(it,"gemini_job/spec.json").exists() }?.sortedByDescending { maxOf(File(it,"story.json").lastModified(),File(it,"gemini_job/status.json").lastModified()) }.orEmpty()
        choiceMenu("📚 เรื่องของฉัน",folders.map { f ->
            runCatching { JSONObject(File(f,"story.json").readText()).optString("title") }.getOrElse { runCatching { GeminiStoryFiles.read(File(f,"gemini_job/outline.json")).getString("title")+" • ยังเขียนไม่จบ" }.getOrDefault("งานเขียนบทที่ยังไม่จบ") } + if(File(f,"archived").exists()) " • เก็บแล้ว" else ""
        }) { i ->
            prefs.edit().putString("project",folders[i].name).apply(); render()
            if(plan==null) { geminiJobMenu(); return@choiceMenu }
            choiceMenu("จัดการเรื่อง",listOf("▶ ทำต่อ", "✏️ เปลี่ยนชื่อ", "📑 ทำสำเนา", "📁 เก็บ / เปิดใช้งาน", "🗑️ ลบเรื่อง")) { action -> when(action) {
                0 -> resumeScene()
                1 -> { val input = EditText(this).apply { setText(plan?.optString("title")) }
                    AlertDialog.Builder(this).setTitle("ชื่อเรื่อง").setView(input).setPositiveButton("บันทึก") { _,_ ->
                        if(input.text.isNotBlank()) { plan?.put("title",input.text.toString().trim()); saveStory(); render() }
                    }.setNegativeButton("ยกเลิก",null).showDirector() }
                2 -> background { val source = project; val target = File(base,UUID.randomUUID().toString()); check(source.copyRecursively(target))
                    val p = JSONObject(File(target,"story.json").readText()); p.put("title",p.optString("title")+" (สำเนา)"); File(target,"story.json").writeText(p.toString(2)); prefs.edit().putString("project",target.name).commit() }
                3 -> { val file = File(project,"archived"); if(file.exists()) file.delete() else file.writeText("archived"); render() }
                4 -> { val folder = project; AlertDialog.Builder(this).setMessage("ลบเรื่องและไฟล์ทั้งหมด? สำรอง ZIP ก่อนลบ")
                    .setPositiveButton("ลบเรื่อง") { _,_ -> folder.deleteRecursively(); prefs.edit().putString("project",UUID.randomUUID().toString()).apply(); render() }
                    .setNegativeButton("ยกเลิก",null).showDirector() }
            } }
        }
    }
    private fun settingsMenu() {
        choiceMenu("⚙️ ตั้งค่าและดูแลงาน",listOf("🔄 อัปเดตแอป", "📦 ส่งออกงานทั้งหมด / สำรอง", "📥 กู้คืนชุดสำรอง", "🎭 ตัวเลือกเดิม / MASTER", "💾 พื้นที่และข้อผิดพลาด", "↶ กู้คืนบทก่อนแก้ล่าสุด", "🎛️ ตั้งค่าการสร้างเรื่อง", "🛟 สำรองทั้งเรื่อง รวม MASTER และเวอร์ชัน", "↗ ส่งคำสั่งล่าสุดซ้ำ")) { i -> when(i) {
            0 -> updater.check()
            1 -> { if(plan == null) notice("ยังไม่มีบท") else { exportEpisode=null; backupMode=false; zipPicker.launch(StoryFiles.zipName(plan!!.optString("title"))) } }
            2 -> restorePicker.launch(arrayOf("application/zip"))
            3 -> toolsMenu()
            4 -> { val used = base.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                val message = "DIRECTOR X ${packageManager.getPackageInfo(packageName,0).versionName}\nงานทั้งหมด ${used/1024/1024} MB\nพื้นที่ว่าง ${filesDir.usableSpace/1024/1024} MB\nเรื่อง ${plan?.optString("title").orEmpty()}\n${File(filesDir,"last_error.txt").takeIf { it.exists() }?.readText().orEmpty()}"
                AlertDialog.Builder(this).setMessage(message).setPositiveButton("ปิด",null).setNeutralButton("คัดลอก") { _,_ -> copyCommand(message) }.showDirector() }
            5 -> { val previous=File(project,"story.previous.json"); if(!previous.exists()) notice("ยังไม่มีบทก่อนแก้") else AlertDialog.Builder(this).setMessage("กู้คืนบทก่อนแก้ล่าสุด? ไฟล์ภาพยังอยู่")
                .setPositiveButton("กู้คืน") { _,_ -> val old=previous.readText(); saveStory(); File(project,"story.json").writeText(old); render() }.setNegativeButton("ยกเลิก",null).showDirector() }
            6 -> planningSettings()
            8 -> retryLastCommand()
            7 -> { if(plan == null) notice("ยังไม่มีบท") else { backupMode=true; zipPicker.launch(StoryFiles.zipName(plan!!.optString("title")+"_สำรอง")) } }
        } }
    }
    private val restorePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) AlertDialog.Builder(this).setTitle("กู้คืนชุดสำรอง")
            .setMessage("${displayName(uri)}\nจะกู้คืนเป็นเรื่องใหม่ ไม่เขียนทับเรื่องเดิม รวม MASTER ภาพ วิดีโอ และเวอร์ชันที่มีในชุดสำรอง")
            .setPositiveButton("กู้คืน") { _,_ -> background {
                val staging=File(cacheDir,"restore_${UUID.randomUUID()}")
                try {
                    contentResolver.openInputStream(uri)!!.use { StudioArchive.read(it,staging) }
                    val p=JSONObject(File(staging,"story.json").readText()); validate(p)
                    val folder=File(base,UUID.randomUUID().toString()); check(staging.copyRecursively(folder))
                    prefs.edit().putString("project",folder.name).commit()
                } finally { staging.deleteRecursively() }
            } }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun retryLastImage() {
        val file=File(project,"last_image_payload.json")
        if(!file.isFile) return notice("ยังไม่มีชุดสร้างภาพล่าสุด กรุณาเลือกรูปปกหรือซีนเพื่อส่งก่อน")
        runCatching {
            val payload=JSONObject(file.readText()); val images=payload.getJSONArray("images")
            require(images.length()>0) { "ไม่พบภาพอ้างอิงในชุดล่าสุด กรุณาเลือกปกหรือซีนอีกครั้ง" }
            val references=(0 until images.length()).map { i ->
                val uri=Uri.parse(images.getString(i))
                require(uri.authority=="$packageName.fileprovider") { "แหล่งภาพส่งซ้ำไม่ถูกต้อง" }
                val relative=uri.path?.trimStart('/') ?: error("ไม่มีเส้นทางภาพ")
                require(relative.startsWith("character_master_exports/") || relative.startsWith("scene_reference_exports/")) { "ภาพไม่ได้อยู่ในพื้นที่ส่งออก" }
                val source=File(cacheDir,relative)
                require(source.canonicalPath.startsWith(cacheDir.canonicalPath+File.separator) && source.isFile) { "ไฟล์ชั่วคราวหมดอายุ กรุณาเลือกปกหรือซีนเดิมเพื่อส่งใหม่" }
                source.nameWithoutExtension to source
            }
            shareImageCommand(payload.getString("text"),references)
        }.onFailure { notice(it.message ?: "ส่งรูปซ้ำไม่ได้") }
    }
    private fun retryLastCommand() {
        val file=File(project,"last_payload.json")
        if(!file.exists()) return notice("ยังไม่มีคำสั่งที่เตรียมส่ง")
        runCatching {
            val payload=JSONObject(file.readText()); val images=payload.getJSONArray("images")
            val references=(0 until images.length()).map { i ->
                val uri=Uri.parse(images.getString(i))
                // Resolve only the app's own export roots, never an external path.
                require(uri.authority=="$packageName.fileprovider") { "แหล่งภาพส่งซ้ำไม่ถูกต้อง" }
                val relative=uri.path?.trimStart('/') ?: error("ไม่มีเส้นทางภาพ")
                require(relative.startsWith("character_master_exports/") || relative.startsWith("scene_reference_exports/")) { "ภาพไม่ได้อยู่ในพื้นที่ส่งออก" }
                val source=File(cacheDir,relative)
                require(source.canonicalPath.startsWith(cacheDir.canonicalPath+File.separator) && source.isFile) { "ภาพส่งซ้ำหมดอายุ กรุณาส่งจากเมนูฉากอีกครั้ง" }
                source.nameWithoutExtension to source
            }
            shareText(payload.getString("text"),lockCreation=payload.optBoolean("creation"),includeMasters=false,referenceImages=references)
        }.onFailure { notice(it.message ?: "ส่งซ้ำไม่ได้") }
    }
    private fun planningSettings() {
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        val fields=listOf("episodes" to "จำนวน EP (ว่าง = AI ประเมิน)", "scenes" to "ฉากต่อ EP (ว่าง = AI ประเมิน)", "duration" to "วินาทีต่อฉาก", "ratio" to "สัดส่วนภาพ 9:16 เท่านั้น")
        val inputs=fields.map { (id,label) -> id to EditText(this).apply { hint=label; setText(if(id=="ratio") "9:16" else prefs.getString("plan_$id",if(id=="duration") "8" else "")); if(id=="ratio") isEnabled=false; panel.addView(this) } }
        AlertDialog.Builder(this).setTitle("ค่าตั้งต้นสำหรับเรื่องใหม่").setView(panel).setPositiveButton("บันทึก") { _,_ ->
            val values=inputs.associate { it.first to it.second.text.toString().trim() }
            if(values["ratio"] !in listOf("9:16") || values["duration"]?.toIntOrNull()?.let { it in 1..60 } != true || listOf("episodes","scenes").any { values[it]!!.isNotBlank() && values[it]?.toIntOrNull()?.let { n -> n in 1..100 } != true }) notice("ตรวจจำนวน 1–100 ระยะเวลา 1–60 และสัดส่วน 9:16")
            else { val edit=prefs.edit(); values.forEach { (k,v) -> edit.putString("plan_$k",v) }; edit.apply(); notice("บันทึกค่าตั้งต้นแล้ว ใช้กับคำสั่งสร้างเรื่องใหม่") }
        }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private var castTarget: Pair<String,File>?
        get() { val name=prefs.getString("cast_target_name",null) ?: return null; val path=prefs.getString("cast_target_path",null) ?: return null; return name to File(path) }
        set(value) { prefs.edit().putString("cast_target_name",value?.first).putString("cast_target_path",value?.second?.path).apply() }
    private val castPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target=castTarget; castTarget=null
        if(uri!=null && target!=null) background {
            val file=target.second; file.parentFile!!.mkdirs(); val temp=File(file.parentFile,file.name+".pending")
            try {
                contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }; android.graphics.BitmapFactory.decodeFile(temp.path,bounds)
                require(bounds.outWidth>0) { "อ่าน MASTER ไม่ได้" }
                if(file.exists()) archiveVersion(file,project,target.first,"master")
                check(temp.renameTo(file))
                val affected=rows(plan!!).filter { scene -> scene.optJSONArray("characters")?.let { a -> (0 until a.length()).any { a.getJSONObject(it).optString("name")==target.first } } == true }
                affected.forEach { it.put("user_review","needs_revision"); it.remove("flow_image_revision") }; saveStory()
            } finally { temp.delete() }
        }
    }
    private fun readSupportingCast(): JSONArray = runCatching { JSONArray(File(filesDir,"supporting_cast.json").readText()) }.getOrDefault(JSONArray())
    private val supportImagePicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pending=prefs.getString("pending_support",null); prefs.edit().remove("pending_support").apply()
        if(uri!=null && pending!=null) runCatching {
            val person=JSONObject(pending); val id=FlowCast.id(person)!!
            val file=File(filesDir,"supporting_masters/$id.master"); file.parentFile!!.mkdirs()
            val temp=File(file.parentFile,file.name+".pending")
            try {
                contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                require(masterExportName(temp,false)!=null) { "อ่านภาพตัวละครเสริมไม่ได้" }
                check(temp.renameTo(file))
                val list=readSupportingCast(); list.put(person)
                require(FlowCast.issues(JSONObject().put("supporting_characters",list)).isEmpty()) { "ชื่อซ้ำหรือข้อมูลตัวละครเสริมไม่ถูกต้อง" }
                File(filesDir,"supporting_cast.json").writeText(list.toString(2))
            } finally { temp.delete() }
        }.onSuccess { notice("บันทึกตัวละครเสริมแล้ว • ใช้กับเรื่องใหม่ที่สร้างต่อจากนี้") }.onFailure { notice(it.message ?: "บันทึกไม่ได้") }
    }
    private fun supportingCastMenu() {
        val roster=readSupportingCast()
        choiceMenu("ตัวละครเสริม • ใช้กับเรื่องใหม่",(0 until roster.length()).map { roster.getJSONObject(it).getString("name") }+"เพิ่มตัวละครเสริม") { index ->
            if(index==roster.length()) {
                if(roster.length()>=8) { notice("รองรับตัวละครเสริมไม่เกิน 8 คน"); return@choiceMenu }
                val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
                val name=EditText(this).apply { hint="ชื่อตัวละครเสริม"; panel.addView(this) }
                val description=EditText(this).apply { hint="ผู้ใหญ่ • บทบาท / ใบหน้า / ผม / รูปร่างที่เห็นจากภาพ"; panel.addView(this) }
                AlertDialog.Builder(this).setTitle("เพิ่มตัวละครเสริมพร้อมภาพต้นฉบับ").setView(panel).setPositiveButton("เลือกภาพ") { _,_ ->
                    val value=name.text.toString().trim()
                    if(value.isBlank() || description.text.isBlank() || StoryIdentity.keyFor(value)!=null || (0 until roster.length()).any { roster.getJSONObject(it).getString("name")==value }) notice("ระบุชื่อไม่ซ้ำและคำบรรยายจากภาพ")
                    else {
                        val id=UUID.randomUUID().toString().take(8).uppercase()
                        val person=JSONObject().put("name",value).put("master","SUPPORT_${id}_MASTER.png").put("visual_description","adult fictional character; "+description.text.toString())
                        prefs.edit().putString("pending_support",person.toString()).apply(); supportImagePicker.launch(arrayOf("image/*"))
                    }
                }.setNegativeButton("ยกเลิก",null).showDirector()
            } else {
                val person=roster.getJSONObject(index); val file=File(filesDir,"supporting_masters/${FlowCast.id(person)}.master")
                choiceMenu(person.getString("name"),listOf("ดูภาพต้นฉบับ","นำออกจากเรื่องใหม่")) { action ->
                    if(action==0) fullImage(file,person.getString("name")) else {
                        val remaining=JSONArray(); for(i in 0 until roster.length()) if(i!=index) remaining.put(roster.getJSONObject(i))
                        File(filesDir,"supporting_cast.json").writeText(remaining.toString(2)); notice("นำออกจากรายการเรื่องใหม่แล้ว เรื่องเดิมยังเก็บภาพของตัวเอง")
                    }
                }
            }
        }
    }
    private fun castMenu() {
        val p=plan ?: return characterMasters()
        val people=rows(p).flatMap { scene -> scene.optJSONArray("characters")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty() }.distinctBy { it.optString("name") }
        choiceMenu("🎭 ตัวละครเรื่องนี้",people.map { "${it.optString("name")} • ${FlowCast.supporting(p).firstOrNull { c -> c.optString("name")==it.optString("name") }?.optString("library_id").orEmpty()} • ${if(masterFor(it).exists()) "มี MASTER" else "ขาด MASTER"}" }) { i ->
            val person=people[i]; val name=person.optString("name")
            choiceMenu(name,listOf("🖼️ ดู MASTER", "📷 เปลี่ยน MASTER รายเรื่อง", "✏️ บทบาทตัวละคร", "🧠 ข้อมูลใบหน้า ผม และรูปร่าง", "เตรียม MASTER มาตรฐาน", "ตรวจภาพ MASTER")) { action -> when(action) {
                0 -> { val file=masterFor(person); if(file.exists()) fullImage(file,name) else notice("ยังไม่มี MASTER") }
                1 -> { val id=FlowCast.id(person)
                    val file=if(p.optString("character_mode","original")=="new") masterFor(person) else if(id!=null) File(project,"original_masters/$id.master") else return@choiceMenu
                    AlertDialog.Builder(this).setMessage("เปลี่ยน MASTER ของ $name เฉพาะเรื่องนี้ • ฉากที่ใช้คนนี้จะถูกระบุว่าต้องตรวจใหม่")
                        .setPositiveButton("เลือกรูป") { _,_ -> castTarget=name to file; castPicker.launch(arrayOf("image/*")) }.setNegativeButton("ยกเลิก",null).showDirector() }
                2 -> { val input=EditText(this).apply { hint="บทบาทและลักษณะตัวละคร"; setText(p.optJSONObject("cast_roles")?.optString(name)) }
                    AlertDialog.Builder(this).setTitle(name).setView(input).setPositiveButton("บันทึก") { _,_ -> val roles=p.optJSONObject("cast_roles") ?: JSONObject(); roles.put(name,input.text.toString()); p.put("cast_roles",roles); saveStory() }.setNegativeButton("ยกเลิก",null).showDirector() }
                3 -> editCharacterMemory(memoryId(person),name,masterFor(person))
                4 -> sendProductionMaster(memoryId(person),name,masterFor(person))
                5 -> reviewMaster(memoryId(person),name,masterFor(person))
            } }
        }
    }

    private fun moviesMenu() {
        val p=plan ?: return notice("นำเข้าบทและคลิปก่อนครับ")
        val eps=p.getJSONArray("episodes")
        choiceMenu("🎬 เลือก EP เพื่อทำวิดีโอ",(0 until eps.length()).map { i ->
            val ep=eps.getJSONObject(i)
            val scenes=rows(p).filter { it.getInt("ep")==ep.getInt("ep") }
            val count=scenes.count { video(it).isFile && video(it).length()>0 }
            "EP ${ep.getInt("ep")} • ${ep.optString("title")}\nคลิป $count/${scenes.size} • " + if(count==scenes.size && scenes.isNotEmpty()) "พร้อมตรวจและรวม EP" else "นำเข้า MP4 รายฉากก่อนรวม"
        }) { i -> movieEpisode(eps.getJSONObject(i)) }
    }
    private fun movieEpisode(ep: JSONObject) {
        choiceMenu("EP ${ep.getInt("ep")} • ${ep.optString("title")}",listOf("🎬 ตรวจคลิปและรวม EP", "⏳ ดูความคืบหน้า / ยกเลิก", "▶ ดูหนังที่รวมแล้ว", "⬇ ดาวน์โหลด MP4", "↗ แชร์ MP4", "🗂️ หนังเวอร์ชันก่อนหน้า", "🎞️ จัดการคลิปฉาก", "↕ จัดลำดับคลิป")) { i -> when(i) {
            0 -> prepareMovie(ep)
            1 -> movieProgress(ep.getInt("ep"))
            2 -> latestMovie(ep.getInt("ep"))?.let { playMovie(it) } ?: notice("ยังไม่มีหนังที่รวมสำเร็จ")
            3 -> latestMovie(ep.getInt("ep"))?.let { downloadMovie(it) } ?: notice("ยังไม่มีหนังที่รวมสำเร็จ")
            4 -> latestMovie(ep.getInt("ep"))?.let { shareMovie(it) } ?: notice("ยังไม่มีหนังที่รวมสำเร็จ")
            5 -> { val jobs=movieJobs(ep.getInt("ep")).filter { File(it,"output.mp4").exists() && readMovieState(it).optString("state")=="complete" }
                choiceMenu("เลือกเวอร์ชัน",jobs.map { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified())) }) { n ->
                    choiceMenu("หนังเวอร์ชันนี้",listOf("ดูหนัง", "ดาวน์โหลด MP4", "แชร์")) { action -> when(action) { 0 -> playMovie(jobs[n]); 1 -> downloadMovie(jobs[n]); 2 -> shareMovie(jobs[n]) } }
                }
            }
            6 -> episodesMenu()
            7 -> reorderMovie(ep)
        } }
    }
    private fun orderedMovieScenes(ep: JSONObject): List<JSONObject> {
        val scenes=rows(plan!!).filter { it.getInt("ep")==ep.getInt("ep") }.sortedBy { it.getInt("scene") }
        val order=ep.optJSONArray("render_order") ?: return scenes
        val keys=(0 until order.length()).map { order.getString(it) }
        require(keys.size==scenes.size && keys.toSet()==scenes.map { key(it) }.toSet()) { "ลำดับคลิปไม่ตรงบท กรุณาจัดลำดับใหม่หรือคืนลำดับเลขฉาก" }
        return keys.map { id -> scenes.first { key(it)==id } }
    }
    private fun reorderMovie(ep: JSONObject) {
        val scenes=rows(plan!!).filter { it.getInt("ep")==ep.getInt("ep") }.sortedBy { it.getInt("scene") }
        val ordered=runCatching { orderedMovieScenes(ep) }.getOrDefault(scenes)
        val ids=ordered.map { key(it) }.toMutableList()
        choiceMenu("จัดลำดับ • เลือกคลิปเพื่อเลื่อน",ids.mapIndexed { i,id -> "${i+1}. $id" }+"คืนลำดับเลขฉาก") { index ->
            if(index==ids.size) { ep.remove("render_order"); saveStory(); reorderMovie(ep) }
            else choiceMenu(ids[index],listOf("เลื่อนขึ้น", "เลื่อนลง")) { action ->
                val destination=if(action==0) index-1 else index+1
                if(destination !in ids.indices) notice("อยู่ปลายลำดับแล้ว") else {
                    val item=ids.removeAt(index); ids.add(destination,item); ep.put("render_order",JSONArray(ids)); saveStory(); reorderMovie(ep)
                }
            }
        }
    }
    private fun movieJobs(ep: Int) = File(project,"movies/EP%02d".format(ep)).listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }.orEmpty()
    private fun readMovieState(job: File): JSONObject = runCatching { JSONObject(File(job,"status.json").readText()) }.getOrDefault(JSONObject())
    private fun latestMovie(ep: Int) = movieJobs(ep).firstOrNull { readMovieState(it).optString("state")=="complete" && File(it,"output.mp4").isFile && runCatching { JSONObject(File(it,"result.json").readText()).let { result -> result.optInt("width")==EpisodeRules.WIDTH && result.optInt("height")==EpisodeRules.HEIGHT } }.getOrDefault(false) }
    private fun movieFingerprint(ep: Int): String {
        val episodes=plan!!.getJSONArray("episodes")
        val episode=(0 until episodes.length()).map { episodes.getJSONObject(it) }.first { it.getInt("ep")==ep }
        val scenes=orderedMovieScenes(episode)
        val cover=coverFile("EP%02d_COVER_9x16".format(ep))
        return "9:16|1080x1920|"+scenes.joinToString(",") { key(it) }+"|"+(listOf(cover)+scenes.map { video(it) }).joinToString("|") { if(it.exists()) EpisodeMedia.digest(it) else "missing" }
    }
    private fun prepareMovie(ep: JSONObject) {
        if(plan?.optString("aspect_ratio","16:9")!="9:16") { landscapeCopyMenu(); return }
        if(EpisodeRenderService.running) return movieProgress(ep.getInt("ep"))
        val number=ep.getInt("ep"); val scenes=runCatching { orderedMovieScenes(ep) }.getOrElse { notice(it.message ?: "ตรวจลำดับคลิป"); return }
        val missing=scenes.filter { !video(it).exists() }
        if(missing.isNotEmpty()) return notice("ยังขาดคลิป ${missing.joinToString { key(it) }}\nนำเข้าให้ครบก่อนรวม")
        val cover=coverFile("EP%02d_COVER_9x16".format(number))
        if(!cover.exists()) return notice("ยังขาดปกแนวตั้ง EP $number • นำเข้าปกก่อนรวม")
        val dialog=AlertDialog.Builder(this).setMessage("กำลังตรวจขนาด เสียง และไฟล์คลิป…").setCancelable(false).showDirector()
        val folder=project; val p=plan!!
        Thread {
            try {
                val infos=scenes.map { EpisodeMedia.inspect(video(it)) }
                val duration=3000L+infos.sumOf { it.durationMs }
                val rate=kotlin.math.round(infos.first().fps).toInt().coerceIn(1,120)
                val bitrate=maxOf(24_000_000,infos.maxOf { it.bitrate }).coerceAtMost(80_000_000)
                val needed=EpisodeRules.estimateBytes(duration,bitrate,infos.sumOf { it.file.length() })
                require(folder.usableSpace>needed) { "พื้นที่ไม่พอ ต้องมีว่างประมาณ ${needed/1024/1024} MB" }
                val fingerprint=movieFingerprint(number)
                val report="ปกเปิด 3 วินาที → ${scenes.size} ฉาก\nผลลัพธ์ 1080 × 1920 • $rate fps • ${duration/1000} วินาที\nพื้นที่ประมาณ ${needed/1024/1024} MB\n\n"+infos.mapIndexed { i,v -> "${key(scenes[i])}: ${v.displayWidth} × ${v.displayHeight}, ${v.fps} fps, ${v.durationMs/1000.0} วินาที, ${v.videoMime}, เสียง ${v.audioMime ?: "ไม่มี"}" }.joinToString("\n")
                runOnUiThread {
                    dialog.dismiss()
                    val view=TextView(this).apply { text=report; setPadding(dp(16),dp(12),dp(16),dp(12)) }
                    AlertDialog.Builder(this).setTitle("ตรวจลำดับและคุณภาพก่อนรวม").setView(ScrollView(this).apply { addView(view) })
                        .setPositiveButton("เลือกรูปแบบรวม") { _,_ ->
                            choiceMenu("วิธีรวม • เก็บต้นฉบับทุกคลิป",listOf("คุณภาพสูง • ปรับไฟล์ให้เข้ากัน", "ไม่เข้ารหัสคลิปใหม่ • ต้องตรงทุกค่า")) { mode ->
                                fun start(crop: Boolean) {
                                    if(EpisodeRenderService.running) { notice("มีงานรวมกำลังทำอยู่"); return }
                                    val job=File(folder,"movies/EP%02d/%d_%s".format(number,System.currentTimeMillis(),UUID.randomUUID())).apply { mkdirs() }
                                    val spec=JSONObject().put("aspect_ratio","9:16").put("width",EpisodeRules.WIDTH).put("height",EpisodeRules.HEIGHT).put("ep",number).put("title",p.optString("title")).put("filename",EpisodeRules.filename(p.optString("title"),number,ep.optString("title")))
                                        .put("cover",cover.path).put("clips",JSONArray(infos.map { it.file.path })).put("fps",rate).put("bitrate",bitrate).put("duration_ms",duration).put("fingerprint",fingerprint).put("input_hashes",JSONArray((listOf(cover)+infos.map { it.file }).map { EpisodeMedia.digest(it) })).put("lossless",mode==1).put("crop",crop).put("has_audio",infos.any { it.audioMime!=null })
                                    File(job,"spec.json").writeText(spec.toString(2))
                                    File(job,"status.json").writeText(JSONObject().put("state","queued").put("message","กำลังเริ่มรวม").toString())
                                    try { androidx.core.content.ContextCompat.startForegroundService(this,Intent(this,EpisodeRenderService::class.java).putExtra("job",job.path)); movieProgress(number) }
                                    catch(e: Exception) { File(job,"status.json").writeText(JSONObject().put("state","failed").put("message",e.message).toString()); notice("เริ่มรวมไม่ได้: ${e.message}") }
                                }
                                if(mode==1 && infos.any { it.width!=EpisodeRules.WIDTH || it.height!=EpisodeRules.HEIGHT || it.rotation!=0 || kotlin.math.abs(it.fps-infos.first().fps)>0.1f }) { notice("ขนาด/เฟรมเรตต่างกัน ต้องเลือกปรับไฟล์คุณภาพสูง"); return@choiceMenu }
                                val message=if(mode==0) "ปกและคลิปจะเข้ารหัสใหม่เป็น H.264 คุณภาพสูง ${bitrate/1_000_000} Mbps เสียง AAC รักษาเสียงเดิม ไม่มีดนตรีเพิ่ม คุณภาพอาจเปลี่ยนเล็กน้อย\nไฟล์ขนาดเล็กกว่าจะถูกขยาย แต่ไม่เพิ่มรายละเอียดจริง" else "สร้างปกเปิดก่อน แล้วตรวจ codec/config และเสียง ถ้าไม่ตรงจะหยุดและแจ้ง ไม่แปลงคลิปเอง"
                                AlertDialog.Builder(this).setTitle("ยืนยันวิธีรวม").setMessage(message).setPositiveButton("ดำเนินการ") { _,_ ->
                                    if(mode==0 && infos.any { it.width.toDouble()/it.height !in 1.76..1.79 || it.rotation!=0 }) choiceMenu("จัดภาพต่างสัดส่วน",listOf("เว้นขอบ • เห็นภาพครบ", "ครอปกึ่งกลาง • เต็มจอ อาจตัดขอบ")) { layout -> movieLayoutPreview(infos.first { it.width.toDouble()/it.height !in 1.76..1.79 || it.rotation!=0 }.file,layout==1) { start(layout==1) } }
                                    else start(false)
                                }.setNegativeButton("ยกเลิก",null).showDirector()
                            }
                        }.setNegativeButton("ยกเลิก",null).showDirector()
                }
            } catch(e: Exception) { runOnUiThread { dialog.dismiss(); notice("ตรวจคลิปไม่ผ่าน: ${e.message}") } }
        }.start()
    }
    private fun movieLayoutPreview(file: File, crop: Boolean, proceed: () -> Unit) {
        val reader=android.media.MediaMetadataRetriever()
        val bitmap=runCatching {
            reader.setDataSource(file.path)
            if(android.os.Build.VERSION.SDK_INT>=27) reader.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,960,540) else reader.getFrameAtTime(0)
        }.getOrNull()
        reader.release()
        val view=ImageView(this).apply {
            setImageBitmap(bitmap); setBackgroundColor(Color.BLACK)
            scaleType=if(crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            layoutParams=LinearLayout.LayoutParams(dp(288),dp(162))
        }
        val panel=LinearLayout(this).apply { orientation=1; gravity=Gravity.CENTER; addView(view) }
        val dialog=AlertDialog.Builder(this).setTitle(if(crop) "ตัวอย่างครอปกึ่งกลาง 9:16" else "ตัวอย่างเว้นขอบ 9:16").setView(panel)
            .setPositiveButton("ใช้รูปแบบนี้") { _,_ -> proceed() }.setNegativeButton("ยกเลิก",null).create()
        dialog.setOnDismissListener { view.setImageDrawable(null); bitmap?.recycle() }; dialog.show()
    }
    private fun movieProgress(ep: Int) {
        val job=movieJobs(ep).firstOrNull() ?: return notice("ยังไม่มีงานรวม EP นี้")
        val text=TextView(this).apply { setPadding(dp(16),dp(16),dp(16),dp(16)); textSize=16f }
        val dialog=AlertDialog.Builder(this).setTitle("รวมหนัง EP $ep").setView(text).setPositiveButton("ปิดหน้าต่าง",null).setNeutralButton("ยกเลิกงาน") { _,_ ->
            if(EpisodeRenderService.running) startService(Intent(this,EpisodeRenderService::class.java).setAction(EpisodeRenderService.CANCEL).putExtra("job",job.path))
        }.create()
        val handler=android.os.Handler(android.os.Looper.getMainLooper())
        var ticks=0
        val update=object : Runnable { override fun run() {
            if(!dialog.isShowing) return
            val status=readMovieState(job); val state=status.optString("state")
            val interrupted=state in listOf("preparing","rendering","queued") && !EpisodeRenderService.running && ++ticks>5
            text.text=if(interrupted) "งานถูกหยุดก่อนเสร็จ กรุณารวมใหม่ • ต้นฉบับยังอยู่" else "${status.optString("message")}\n${status.optInt("progress")}%\n${if(state=="complete") "กลับเมนู EP เพื่อดูหนังหรือดาวน์โหลด MP4" else "สลับแอปได้ งานยังทำต่อผ่านบริการเบื้องหน้า"}"
            handler.postDelayed(this,1000)
        } }
        dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null) }; dialog.show(); handler.post(update)
    }
    private fun checkMovieChanges(job: File, proceed: () -> Unit) {
        val ep=JSONObject(File(job,"spec.json").readText()).getInt("ep")
        val old=JSONObject(File(job,"result.json").readText()).optString("fingerprint")
        val dialog=AlertDialog.Builder(this).setMessage("ตรวจว่าคลิปและปกเปลี่ยนหรือไม่…").setCancelable(false).showDirector()
        Thread { val changed=runCatching { movieFingerprint(ep)!=old }.getOrDefault(true); runOnUiThread {
            dialog.dismiss()
            if(changed) AlertDialog.Builder(this).setMessage("คลิปหรือปกเปลี่ยนหลังรวม หนังนี้เป็นเวอร์ชันเดิม ต้องรวมใหม่เพื่อใช้การแก้ล่าสุด")
                .setPositiveButton("ใช้เวอร์ชันเดิม") { _,_ -> proceed() }.setNegativeButton("ยกเลิก",null).showDirector() else proceed()
        } }.start()
    }
    private fun playMovie(job: File) {
        val file=File(job,"output.mp4"); val view=VideoView(this).apply { setVideoPath(file.path); val controls=MediaController(this@StudioActivity); controls.setAnchorView(this); setMediaController(controls); setOnPreparedListener { start() }; setOnErrorListener { _,_,_ -> notice("เล่นหนังไฟล์นี้ไม่ได้"); true } }
        val dialog=AlertDialog.Builder(this).setTitle("หนัง EP").setView(view).setPositiveButton("ปิด",null).create(); dialog.setOnDismissListener { view.stopPlayback() }; dialog.show(); dialog.window?.setLayout(-1,dp(560))
    }
    private val movieDownloadPicker=registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val path=prefs.getString("movie_download_job",null)
        if(uri!=null && path!=null) {
            val dialog=AlertDialog.Builder(this).setMessage("กำลังบันทึก MP4 ลงตำแหน่งที่เลือก…").setCancelable(false).showDirector()
            Thread {
                try {
                    val job=File(path); val file=File(job,"output.mp4"); require(file.isFile) { "ไม่พบ MP4" }
                    contentResolver.openOutputStream(uri,"wt")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
                    val size=contentResolver.openFileDescriptor(uri,"r")?.use { it.statSize }
                    require(size==null || size<0 || size==file.length()) { "บันทึกไฟล์ไม่ครบ" }
                    File(job,"downloaded.json").writeText(JSONObject().put("uri",uri.toString()).put("time",System.currentTimeMillis()).toString())
                    runOnUiThread { dialog.dismiss(); notice("ดาวน์โหลด MP4 สำเร็จ • ${displayName(uri)}") }
                } catch(e: Exception) { runOnUiThread { dialog.dismiss(); notice("ดาวน์โหลดไม่สำเร็จ: ${e.message} • ไฟล์ที่ตำแหน่งปลายทางอาจยังไม่ครบ กรุณาบันทึกใหม่") } }
            }.start()
        }
        prefs.edit().remove("movie_download_job").apply()
    }
    private fun downloadMovie(job: File) = checkMovieChanges(job) {
        val filename=JSONObject(File(job,"result.json").readText()).getString("filename")
        prefs.edit().putString("movie_download_job",job.path).apply(); movieDownloadPicker.launch(filename)
    }
    private fun shareMovie(job: File) = checkMovieChanges(job) {
        val file=File(job,"output.mp4")
        val name=JSONObject(File(job,"result.json").readText()).getString("filename")
        val export=File(cacheDir,"movie_exports/${UUID.randomUUID()}/$name").apply { parentFile!!.mkdirs() }
        background { file.copyTo(export); runOnUiThread {
            val uri=FileProvider.getUriForFile(this,"$packageName.fileprovider",export)
            val intent=Intent(Intent.ACTION_SEND).apply { type="video/mp4"; putExtra(Intent.EXTRA_STREAM,uri); clipData=ClipData.newUri(contentResolver,"หนัง EP",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            runCatching { startActivity(Intent.createChooser(intent,"แชร์หนัง EP")) }.onFailure { notice("เปิดแชร์ไม่ได้: ${it.message}") }
        } }
    }

    private fun review() { episodesMenu() }
    private fun export(uri: Uri) {
        val p = plan!!
        contentResolver.openOutputStream(uri)!!.use { output -> ZipOutputStream(output).use { zip ->
            fun put(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            val delivered = JSONObject(p.toString())
            if(exportEpisode != null) {
                val selected = JSONArray(); val all = p.getJSONArray("episodes")
                for(i in 0 until all.length()) if(all.getJSONObject(i).getInt("ep")==exportEpisode) selected.put(all.getJSONObject(i))
                delivered.put("export_scope","episode").put("source_episode",exportEpisode).put("episodes",selected).put("episode_count",selected.length()).put("scene_count",rows(delivered).size)
            }
            put("story.json",delivered.toString(2).toByteArray())
            put("MISSING.txt", rows(p).filter { exportEpisode == null || it.getInt("ep") == exportEpisode }.joinToString("\n") { "${key(it)}: ภาพ=${image(it).exists()} วิดีโอ=${video(it).exists()}" }.toByteArray())
            put("README.txt","${p.getString("title")}\n${p.getString("category")}\nนำภาพฉากและคำสั่งใน Flow/EPxx_FLOW.txt ไปใช้ใน Flow • 8 วินาที • 9:16".toByteArray())
            for (name in coverNames(p)) {
                val file = coverFile(name)
                if (!file.exists()) continue
                if (exportEpisode != null && StoryFiles.sceneNumber(name + "_SC001")?.first != exportEpisode && !name.startsWith("EP%02d_".format(exportEpisode))) continue
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path,opts)
                val ext = when(opts.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                put("covers/$name.$ext",file.readBytes())
            }
            val episodes = p.getJSONArray("episodes")
            for (i in 0 until episodes.length()) {
                val ep = episodes.getJSONObject(i)
                val number = ep.getInt("ep")
                if (exportEpisode != null && number != exportEpisode) continue
                val scenes = rows(p).filter { it.getInt("ep") == number }
                val commands = StoryFiles.episodeFlow(number, ep.optString("title"), scenes.map {
                    Triple(key(it), "${it.getString("action")}\n${it.optString("dialogue")}", flowCommand(it))
                })
                put("Flow/EP%02d_FLOW.txt".format(number), commands.replace("8 วินาทีต่อซีน • 9:16", "${p.optInt("duration_seconds",8)} วินาทีต่อซีน • ${p.optString("aspect_ratio","9:16")}").toByteArray())
            }
            for (s in rows(p)) {
                if (exportEpisode != null && s.getInt("ep") != exportEpisode) continue
                val path = "EP%02d/%s".format(s.getInt("ep"),key(s))
                val file = image(s)
                val video = video(s)
                if (video.exists()) { zip.putNextEntry(ZipEntry("videos/${key(s)}.mp4")); video.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                if (!file.exists()) continue
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path,opts)
                val extension = when(opts.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                zip.putNextEntry(ZipEntry("$path.$extension")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()

            }
        } }
    }
}


