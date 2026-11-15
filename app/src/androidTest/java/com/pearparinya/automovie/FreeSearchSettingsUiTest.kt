package com.pearparinya.automovie

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeSearchSettingsUiTest {
    @Test fun showsFiveSeparateTestButtons() {
        ActivityScenario.launch(StudioActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog=FreeSearchSettingsUi.show(activity)
                fun buttons(view: View): List<String> = (if(view is Button) listOf(view.text.toString()) else emptyList()) +
                    (if(view is ViewGroup) (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) } else emptyList())
                val visible=buttons(dialog.window!!.decorView)
                listOf("Tavily","Exa","SerpApi","Firecrawl","LangSearch").forEach { assertTrue("missing $it",visible.any { text -> text.contains("ทดสอบ $it") }) }
                dialog.dismiss()
            }
        }
    }
}
