package com.pearparinya.automovie

/** Search every requested storefront; round-robin results so one source cannot hide the rest. */
internal object ProductMultiSearch {
    data class Item(val store: Int, val product: ProductSearch.Result)
    data class Outcome(val items: List<Item>, val storeReports: List<String>)
    fun run(stores: List<Int>, control: ProductSearchControl, search: (Int) -> ProductSearchRotation.Outcome): Outcome {
        val buckets=linkedMapOf<Int,List<ProductSearch.Result>>()
        val reports=mutableListOf<String>()
        val labels=listOf("TikTok Shop","Shopee","Lazada")
        stores.distinct().forEach { store ->
            control.check(); require(store in labels.indices)
            val result=search(store)
            control.check()
            buckets[store]=result.results.distinctBy { it.url }.take(5)
            reports.add(if(result.results.isEmpty()) "${labels[store]}: ${result.failures.lastOrNull()?.substringAfter(": ") ?: "ไม่พบข้อมูล"}" else "${labels[store]}: พบ ${result.results.size} รายการ")
        }
        val combined=mutableListOf<Item>()
        repeat(5) { position ->
            buckets.forEach { (store, products) ->
                products.getOrNull(position)?.let { if(combined.size<5) combined.add(Item(store,it)) }
            }
        }
        return Outcome(combined,reports)
    }
}
