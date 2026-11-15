package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class ProductMultiSearchTest {
    @Test fun searchesAllStoresAndSharesFiveResultsFairly() {
        val calls=mutableListOf<Int>()
        val result=ProductMultiSearch.run(listOf(0,1,2),ProductSearchControl()) { store ->
            calls.add(store)
            ProductSearchRotation.Outcome((1..4).map { ProductSearch.Result("item $store/$it","https://example.test/$store/$it","") },emptyList())
        }
        assertEquals(listOf(0,1,2),calls)
        assertEquals(listOf(0,1,2,0,1),result.items.map { it.store })
        assertEquals(3,result.storeReports.size)
    }
    @Test fun includesFailuresAndDoesNotInventResults() {
        val out=ProductMultiSearch.run(listOf(0,1,2),ProductSearchControl()) { store ->
            if(store==1) ProductSearchRotation.Outcome(listOf(ProductSearch.Result("item","https://example.test/1","")),emptyList())
            else ProductSearchRotation.Outcome(emptyList(),listOf("Gemini: โควตาหมด"))
        }
        assertEquals(listOf(1),out.items.map { it.store }); assertTrue(out.storeReports[0].contains("โควตาหมด"))
    }
    @Test fun cancellationStopsRemainingStores() {
        val control=ProductSearchControl(); val calls=mutableListOf<Int>()
        try { ProductMultiSearch.run(listOf(0,1,2),control) { store -> calls.add(store); control.cancel(); ProductSearchRotation.Outcome(emptyList(),emptyList()) }; fail("expected cancellation") }
        catch(_: CancellationException) { }
        assertEquals(listOf(0),calls)
    }
}
