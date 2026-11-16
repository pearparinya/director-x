package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class ProductSearchRotationTest {
    @Test fun triesIndependentProjectsInOrderAndStopsOnSuccess() {
        val called=mutableListOf<Int>(); val progress=mutableListOf<String>()
        val match=ProductSearch.Result("product","https://shop-th.tiktok.com/pdp/123","")
        val attempts=(1..5).map { n -> ProductSearchRotation.Attempt("Gemini $n") {
            called.add(n); when(n) { 1 -> throw ProductSearchError("โควตาเต็ม"); 2 -> emptyList(); 3 -> listOf(match); else -> error("should stop") }
        } }
        val out=ProductSearchRotation.run(attempts,ProductSearchControl()) { _,_,label,_ -> progress.add(label) }
        assertEquals(listOf(1,2,3),called); assertEquals(listOf(match),out.results)
        assertTrue(out.failures.any { it.contains("โควตาเต็ม") }); assertTrue(out.failures.any { it.contains("ไม่พบ") })
        assertTrue(progress.contains("Gemini 3"))
    }
    @Test fun triesAllFiveBeforeUsingFallback() {
        val called=mutableListOf<String>(); val match=ProductSearch.Result("สินค้า","https://shop-th.tiktok.com/pdp/123","")
        val attempts=(1..5).map { n -> ProductSearchRotation.Attempt("Gemini $n") { called.add("Gemini $n"); emptyList() } } +
            ProductSearchRotation.Attempt("OpenRouter") { called.add("OpenRouter"); listOf(match) }
        val out=ProductSearchRotation.run(attempts,ProductSearchControl()) { _,_,_,_ -> }
        assertEquals(listOf("Gemini 1","Gemini 2","Gemini 3","Gemini 4","Gemini 5","OpenRouter"),called)
        assertEquals(listOf(match),out.results)
    }
    @Test fun cancellationPreventsNextProvider() {
        val control=ProductSearchControl(); var secondCalled=false
        val attempts=listOf(ProductSearchRotation.Attempt("first") { control.cancel(); emptyList() }, ProductSearchRotation.Attempt("second") { secondCalled=true; emptyList() })
        try { ProductSearchRotation.run(attempts,control) { _,_,_,_ -> }; fail("expected cancellation") } catch(_: CancellationException) { }
        assertFalse(secondCalled)
    }
    @Test fun unknownExceptionsNeverExposeSecrets() {
        assertFalse(ProductSearchRotation.friendly(IllegalStateException("secret-key")).contains("secret-key"))
    }

    @Test fun retryTransientTimeoutOnlyOnce() {
        var calls=0
        val match=ProductSearch.Result("สินค้า","https://shopee.co.th/product/123","")
        val result=ProductSearchRotation.run(listOf(ProductSearchRotation.Attempt("Tavily") {
            calls++
            if(calls==1) throw java.net.SocketTimeoutException("timeout")
            listOf(match)
        }),ProductSearchControl()) { _,_,_,_ -> }
        assertEquals(2,calls)
        assertEquals(listOf(match),result.results)
    }
    @Test fun neverRetryInvalidKeyOrQuota() {
        for(code in listOf(401,429)) {
            var calls=0
            ProductSearchRotation.run(listOf(ProductSearchRotation.Attempt("provider") {
                calls++
                throw ProductSearchError("HTTP $code")
            }),ProductSearchControl()) { _,_,_,_ -> }
            assertEquals(1,calls)
        }
    }
    @Test fun retryServerErrorOnceThenFallback() {
        var calls=0
        val match=ProductSearch.Result("สินค้า","https://shopee.co.th/product/123","")
        val result=ProductSearchRotation.run(listOf(
            ProductSearchRotation.Attempt("first") { calls++; throw ProductSearchError("HTTP 503") },
            ProductSearchRotation.Attempt("second") { listOf(match) }
        ),ProductSearchControl()) { _,_,_,_ -> }
        assertEquals(2,calls)
        assertEquals(listOf(match),result.results)
    }
}
