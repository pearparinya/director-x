package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class ProductSearchTest {
    @Test fun rejectsGenericTitlesWithoutDroppingProductNames() {
        assertFalse(ProductSearch.relevantTitle("TikTok Shop Thailand"))
        assertFalse(ProductSearch.relevantTitle("ค้นหา - TikTok Shop Thailand"))
        assertFalse(ProductSearch.relevantTitle("เข้าสู่ระบบเพื่อช้อปออนไลน์ | Shopee Thailand"))
        assertTrue(ProductSearch.relevantTitle("ขาตั้งกล้องมือถือพร้อมไฟวงแหวน"))
    }
    @Test fun acceptsOnlyProductPagesOnTheSelectedPlatform() {
        assertTrue(ProductSearch.matchesPlatform("https://shopee.co.th/product/123/456", 1))
        assertTrue(ProductSearch.matchesPlatform("https://www.lazada.co.th/products/example-i123.html", 2))
        assertTrue(ProductSearch.matchesPlatform("https://shop-th.tiktok.com/pdp/123", 0))
        assertFalse(ProductSearch.matchesPlatform("https://shopee.co.th/search?keyword=drill", 1))
        assertFalse(ProductSearch.matchesPlatform("https://shop-th.tiktok.com/pdp/search", 0))
        assertFalse(ProductSearch.matchesPlatform("https://www.lazada.co.th/products/search", 2))
        assertFalse(ProductSearch.matchesPlatform("https://shopee.co.th/product/123/456", 2))
        assertFalse(ProductSearch.matchesPlatform("https://shopee.co.th.evil.test/product/123/456", 1))
    }
}
