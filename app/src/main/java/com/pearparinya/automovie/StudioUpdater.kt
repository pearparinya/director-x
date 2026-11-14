package com.pearparinya.automovie

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** In-place update from the Studio screen; verifies package, version and signer. */
class StudioUpdater(private val activity: AppCompatActivity) {
    private var busy = false
    private var pending: File? = null
    private val permission = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = pending
        if (file != null && activity.packageManager.canRequestPackageInstalls()) install(file)
        else message("ยังไม่ได้อนุญาตติดตั้งอัปเดต กด Update เพื่อลองอีกครั้งครับ")
    }
    private fun message(text: String) = AlertDialog.Builder(activity).setIcon(R.drawable.studio_update).setTitle("DIRECTOR X").setMessage(text).setPositiveButton("ตกลง",null).showDirector()
    private var lastAutomaticCheck = 0L
    fun checkAutomatically() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastAutomaticCheck != 0L && now - lastAutomaticCheck < 30 * 60 * 1000L) return
        if (busy) return
        lastAutomaticCheck = now
        check(true)
    }
    fun check(automatic: Boolean = false) {
        if (busy) return
        busy = true
        val dialog = if (automatic) null else AlertDialog.Builder(activity).setIcon(R.drawable.studio_update).setTitle("DIRECTOR X").setMessage("กำลังตรวจสอบรุ่นใหม่…").setCancelable(false).showDirector()
        Thread {
            try {
                val connection = connect("https://api.github.com/repos/pearparinya/director-x/releases/latest?cb=${System.currentTimeMillis()}")
                val release = try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }) } finally { connection.disconnect() }
                val build = Regex("BUILD\\s*[:#]?\\s*(\\d+)",RegexOption.IGNORE_CASE).find(release.optString("body"))?.groupValues?.get(1)?.toLongOrNull()
                    ?: Regex("-B(\\d+)").find(release.getString("tag_name"))?.groupValues?.get(1)?.toLongOrNull()
                    ?: error("รุ่นล่าสุดไม่มีข้อมูลการอัปเดต")
                val local = activity.packageManager.getPackageInfo(activity.packageName,0).versionCode.toLong()
                val assets = release.getJSONArray("assets")
                val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk",true) } ?: error("ไม่พบ APK รุ่นใหม่")
                val url = asset.getString("browser_download_url")
                require(url.startsWith("https://github.com/pearparinya/director-x/releases/download/") ||
                    url.startsWith("https://github.com/pearparinya/auto-movie-r6-4/releases/download/")) { "แหล่งดาวน์โหลดไม่ถูกต้อง" }
                activity.runOnUiThread {
                    busy = false; dialog?.dismiss()
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (automatic && !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                        lastAutomaticCheck = 0L
                        return@runOnUiThread
                    }
                    if (build <= local) { if (!automatic) message("คุณใช้ DIRECTOR X รุ่นล่าสุดแล้วครับ") }
                    else AlertDialog.Builder(activity).setIcon(R.drawable.studio_update).setTitle("DIRECTOR X รุ่นใหม่พร้อมแล้ว").setMessage("${release.optString("name", "รุ่นใหม่")}\nดาวน์โหลดและติดตั้งทับเพื่ออัปเดต งานที่บันทึกไว้จะยังอยู่")
                        .setPositiveButton("อัปเดต") { _,_ -> download(url,build,asset.optLong("size",-1)) }.setNegativeButton("ภายหลัง",null).showDirector()
                }
            } catch (e: Exception) { activity.runOnUiThread { busy = false; if(automatic) lastAutomaticCheck=0L; dialog?.dismiss(); if (!automatic && !activity.isFinishing && !activity.isDestroyed) message("ตรวจสอบอัปเดตไม่สำเร็จ: ${e.message}") } }
        }.start()
    }
    private fun connect(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 30000; useCaches = false
        setRequestProperty("User-Agent","Story-Studio-Android")
        setRequestProperty("Cache-Control","no-cache")
        require(responseCode in 200..299) { "เครือข่าย HTTP $responseCode" }
    }
    private fun download(url: String, build: Long, expectedSize: Long) {
        if (busy) return
        busy = true
        val dialog = AlertDialog.Builder(activity).setIcon(R.drawable.studio_update).setTitle("อัปเดต DIRECTOR X").setMessage("กำลังดาวน์โหลด…").setCancelable(false).showDirector()
        Thread {
            var target: File? = null
            try {
                val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("ไม่มีพื้นที่ดาวน์โหลด")
                val file = File(dir,"DIRECTOR-X-$build.apk"); target = file
                val connection = connect(url)
                try { connection.inputStream.use { source -> file.outputStream().use { source.copyTo(it) } } } finally { connection.disconnect() }
                require(file.length() > 100000 && (expectedSize <= 0 || file.length() == expectedSize)) { "ดาวน์โหลดไฟล์ไม่ครบ" }
                val pm = activity.packageManager
                val candidate = pm.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNATURES) ?: error("ไฟล์ APK ไม่ถูกต้อง")
                val installed = pm.getPackageInfo(activity.packageName,PackageManager.GET_SIGNATURES)
                require(candidate.packageName == activity.packageName && candidate.versionCode.toLong() > installed.versionCode.toLong()) { "ไฟล์ไม่ใช่อัปเดตของแอปนี้" }
                fun certificates(info: android.content.pm.PackageInfo) = info.signatures?.map { signature ->
                    MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
                }?.toSet() ?: emptySet()
                val signers = certificates(candidate)
                require(signers.isNotEmpty() && signers == certificates(installed)) { "ลายเซ็นไม่ตรงกับแอปที่ติดตั้ง" }
                activity.runOnUiThread { busy = false; dialog.dismiss(); install(file) }
            } catch (e: Exception) {
                target?.delete()
                activity.runOnUiThread { busy = false; dialog.dismiss(); message("อัปเดตไม่สำเร็จ: ${e.message}") }
            }
        }.start()
    }
    private fun install(file: File) {
        pending = file
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setIcon(R.drawable.studio_update).setTitle("อนุญาตอัปเดต DIRECTOR X").setMessage("เปิดสิทธิ์ติดตั้งจากแอปนี้ แล้วกลับมาเพื่อติดตั้งอัปเดต")
                .setPositiveButton("เปิดการตั้งค่า") { _,_ -> permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${activity.packageName}"))) }
                .setNegativeButton("ภายหลัง",null).showDirector()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(activity,"${activity.packageName}.fileprovider",file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri,"application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
        } catch (e: Exception) { message("เปิดตัวติดตั้งไม่สำเร็จ: ${e.message}") }
    }
}

