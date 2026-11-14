package com.pearparinya.automovie

import org.json.JSONObject
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Selects among configured Gemini slots without persisting or exposing credentials. */
internal class GeminiPoolClient(
    private val slots: List<Slot>,
    private val startSlot: () -> Int,
    private val cancelled: () -> Boolean,
    private val onSlot: (Int) -> Unit,
    private val automaticRetry: Boolean = false,
    private val onRetry: (String) -> Unit = {}
) : StoryTextClient {
    internal data class Slot(val number: Int, val client: StoryTextClient, val cooldownMillis: () -> Long)
    private val stopped=AtomicBoolean(false)
    @Volatile private var current: StoryTextClient?=null
    init { require(slots.isNotEmpty()) { "ยังไม่มี Gemini API Key" } }
    override fun cancel() { stopped.set(true); current?.cancel() }
    override fun useProposalTimeouts() { slots.forEach { it.client.useProposalTimeouts() } }
    override fun availableDelayMillis() = slots.minOf { it.client.availableDelayMillis() }
    override fun generate(prompt: String,schema: JSONObject): JSONObject {
        val isStopped={ stopped.get() || cancelled() || Thread.currentThread().isInterrupted }
        val request={ generateOnce(prompt,schema,isStopped) }
        return if(automaticRetry) StoryRequestRetry().execute(isStopped,onRetry,request) else request()
    }
    private fun generateOnce(prompt: String,schema: JSONObject,isStopped: () -> Boolean): JSONObject {
        val requested=startSlot()
        val start=slots.indexOfFirst { it.number==requested }.coerceAtLeast(0)
        val ordered=slots.drop(start)+slots.take(start)
        var last: StoryHttpFailure?=null
        for(slot in ordered) {
            if(isStopped()) throw CancellationException()
            if(slot.cooldownMillis()>0) continue
            onSlot(slot.number)
            current=slot.client
            if(isStopped()) { current=null; throw CancellationException() }
            try { return slot.client.generate(prompt,schema) }
            catch(e: StoryHttpFailure) {
                // A quota failure is specific to this key; try another configured key.
                if(e.status!=429) throw e
                last=e
            } finally { current=null }
        }
        if(isStopped()) throw CancellationException()
        val remaining=slots.map { it.cooldownMillis() }.filter { it>0 }.minOrNull()
        if(remaining!=null) throw StoryHttpFailure(429,((remaining+999)/1000).toString(),"คีย์ Gemini กำลังพักตามโควตา")
        throw last ?: StoryHttpFailure(429,"65","คีย์ Gemini ยังไม่พร้อม")
    }
}
