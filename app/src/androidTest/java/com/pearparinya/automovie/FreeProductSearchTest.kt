package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeProductSearchTest {
    @Test fun parsesAllFiveAndRejectsGenericOrWrongStorePages() {
        val good="https://shopee.co.th/product/123/456"
        val rows="""[{"title":"เครื่องมือสวน ชุด 5 ชิ้น","name":"เครื่องมือสวน ชุด 5 ชิ้น","url":"$good","link":"$good"},{"title":"เข้าสู่ระบบเพื่อช้อปออนไลน์","name":"เข้าสู่ระบบเพื่อช้อปออนไลน์","url":"$good","link":"$good"},{"title":"หน้าค้นหา","name":"หน้าค้นหา","url":"https://shopee.co.th/search","link":"https://shopee.co.th/search"}]"""
        val fixtures=mapOf("tavily" to "{\"results\":$rows}", "exa" to "{\"results\":$rows}", "serpapi" to "{\"organic_results\":$rows}", "firecrawl" to "{\"data\":{\"web\":$rows}}", "langsearch" to "{\"data\":{\"webPages\":{\"value\":$rows}}}")
        fixtures.forEach { (provider,body) ->
            val result=FreeProductSearch.parseResults(provider,JSONObject(body),1)
            assertEquals(provider,1,result.size); assertEquals(good,result[0].url)
            assertTrue(FreeProductSearch.parseResults(provider,JSONObject(body),2).isEmpty())
        }
    }
}
