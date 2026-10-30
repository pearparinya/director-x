package com.pearparinya.automovie

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Synthetic pixels only; no former character photographs are packaged in tests. */
internal object TestMasterAssets {
    private val pixels: ByteArray by lazy {
        val bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)
        try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray() }
        finally { bitmap.recycle() }
    }
    fun open(path: String): ByteArrayInputStream {
        if(path!="master_library/catalog.json") return ByteArrayInputStream(pixels)
        val data=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("casting_catalog_fixture.json").bufferedReader().use { it.readText() })
        val rows=data.getJSONArray("records")
        for(i in 0 until rows.length()) rows.getJSONObject(i).put("sha256",MasterLibrary.digest(pixels))
        return ByteArrayInputStream(data.toString().toByteArray(Charsets.UTF_8))
    }
}
