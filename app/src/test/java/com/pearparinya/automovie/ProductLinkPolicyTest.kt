package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class ProductLinkPolicyTest {
    @Test fun onlyTrustedHttpsDomainsAreAccepted() {
        assertTrue(ProductLinkPolicy.allowed("https://shope.ee/example?affiliate=1"))
        assertTrue(ProductLinkPolicy.allowed("https://vt.tiktok.com/example"))
        assertTrue(ProductLinkPolicy.allowed("https://th.shp.ee/example"))
        assertTrue(ProductLinkPolicy.allowed("https://www.lazada.co.th/products/test.html"))
        assertFalse(ProductLinkPolicy.allowed("http://shopee.co.th/item"))
        assertFalse(ProductLinkPolicy.allowed("https://shopee.co.th.evil.example/item"))
        assertFalse(ProductLinkPolicy.allowed("https://shopee.co.th@evil.example/item"))
        assertFalse(ProductLinkPolicy.allowed("https://shopee.co.th:8080/item"))
    }
    @Test fun extractsLinkFromSharedText() {
        assertEquals("https://shope.ee/abc?x=1", ProductLinkPolicy.firstUrl("สินค้าดี https://shope.ee/abc?x=1"))
        assertEquals("", ProductLinkPolicy.firstUrl("ไม่มีลิงก์"))
    }
}
