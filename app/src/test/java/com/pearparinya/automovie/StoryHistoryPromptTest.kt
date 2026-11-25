package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class StoryHistoryPromptTest {
    @Test fun completeLedgerAvoidsDuplicateHistory() {
        val ledger = "SC 1: นายเปีย; MASTER; outfit=blue; props=lamp; dialogue=hello"
        val result = StoryContinuityLedger.promptContext(ledger, "duplicate past", "duplicate previous", true)
        assertTrue(result.contains(ledger))
        assertFalse(result.contains("duplicate past"))
        assertFalse(result.contains("duplicate previous"))
    }

    @Test fun truncatedLedgerRetainsRecentSummaries() {
        val ledger = "Earlier entries omitted because the ledger exceeded its size limit; recent scene"
        val result = StoryContinuityLedger.promptContext(ledger, "past", "previous", true)
        assertTrue(result.contains(ledger))
        assertTrue(result.contains("past"))
        assertTrue(result.contains("previous"))
    }

    @Test fun nonCompactModeRetainsLegacySummaries() {
        val result = StoryContinuityLedger.promptContext("ledger", "past", "previous", false)
        assertTrue(result.contains("ledger"))
        assertTrue(result.contains("past"))
        assertTrue(result.contains("previous"))
    }
}
