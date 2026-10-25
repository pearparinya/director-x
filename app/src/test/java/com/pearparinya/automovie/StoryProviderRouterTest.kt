package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class StoryProviderRouterTest {
    @Test fun outlineAndSceneRolesAreDifferent() {
        assertEquals(StoryProvider.GEMINI,StoryProviderRouter.preferred(false))
        assertEquals(StoryProvider.GROQ,StoryProviderRouter.preferred(true))
    }
    @Test fun quotaFallsBackAndAvoidsBlockedProviderUntilDeadline() {
        var time=0L
        val calls=mutableListOf<StoryProvider>()
        val routes=mutableListOf<Pair<StoryProvider,Boolean>>()
        val router=StoryProviderRouter({false},{p,f -> routes.add(p to f)},{time})
        val prompt="pending scene with locked outfit"
        val seen=mutableListOf<String>()
        fun run()=router.generate(StoryProvider.GROQ) { p ->
            calls.add(p); seen.add(prompt)
            if(p==StoryProvider.GROQ && time<90_000L) throw StoryHttpFailure(429,"90","quota")
            "ok"
        }
        assertEquals("ok",run()); assertEquals("ok",run())
        assertEquals(listOf(StoryProvider.GROQ,StoryProvider.GEMINI,StoryProvider.GEMINI),calls)
        assertTrue(routes.last().second); assertTrue(seen.all { it==prompt })
        time=90_000L; run()
        assertEquals(StoryProvider.GROQ,calls.last()); assertFalse(routes.last().second)
    }
    @Test fun bothBlockedWaitForEarliestDeadlineWithoutSendingMoreRequests() {
        var calls=0; var time=0L
        val router=StoryProviderRouter({false},{_,_ ->},{time})
        fun run()=runCatching { router.generate(StoryProvider.GROQ) { p ->
            calls++; throw StoryHttpFailure(429,if(p==StoryProvider.GROQ) "300" else "120","quota")
        } }.exceptionOrNull() as StoryHttpFailure
        assertEquals("120",run().retryAfter); assertEquals(2,calls)
        time=30_000L; assertEquals("90",run().retryAfter); assertEquals(2,calls)
        time=120_000L; run(); assertEquals(3,calls)
    }
    @Test fun authenticationRefusalAndUserStopDoNotTriggerFallback() {
        listOf<Exception>(StoryHttpFailure(401,null,"key"),IllegalStateException("refusal"),CancellationException()).forEach { error ->
            var calls=0
            val failure=runCatching { StoryProviderRouter({false},{_,_->},{0L}).generate(StoryProvider.GEMINI) { calls++; throw error } }.exceptionOrNull()
            assertEquals(1,calls)
            if(error !is CancellationException) assertSame(error,failure) else assertTrue(failure is CancellationException)
        }
        var stopped=false; var calls=0
        val failure=runCatching { StoryProviderRouter({stopped},{_,_ -> stopped=true},{0L}).generate(StoryProvider.GROQ) { calls++; throw StoryHttpFailure(429,null,"quota") } }.exceptionOrNull()
        assertTrue(failure is CancellationException); assertEquals(1,calls)
    }
    @Test fun bothBusyUseBoundedCountdownAndResumeOnePendingRequest() {
        var time=0L; var calls=0
        val router=StoryProviderRouter({false},{_,_->},{time})
        val result=StoryRequestRetry({time},{time+=it}).execute({false},{}) {
            router.generate(StoryProvider.GROQ) {
                calls++
                if(calls<=2) throw StoryHttpFailure(503,"70","temporary")
                "same batch completed"
            }
        }
        assertEquals("same batch completed",result); assertEquals(3,calls); assertEquals(70_000L,time)
    }
}
