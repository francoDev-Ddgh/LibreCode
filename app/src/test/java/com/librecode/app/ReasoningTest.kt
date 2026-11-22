package com.librecode.app

import org.junit.Assert.*
import org.junit.Test

class ReasoningTest {
    @Test fun geminiLevelsAreModelSpecific() {
        val flash = Reasoning.known(0, "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent", "gemini-3.5-flash")!!
        assertEquals("gemini_level", flash.mode)
        assertTrue(flash.options.any { it.key == "medium" })
        assertFalse(flash.options.any { it.key == "none" })
        val old = Reasoning.known(0, "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", "gemini-2.5-flash")!!
        assertEquals("gemini_budget", old.mode)
        assertTrue(old.options.any { it.key == "0" })
    }
    @Test fun unknownAndThirdPartyAreNotAssumed() {
        assertNull(Reasoning.known(5, "https://example.com/v1/chat/completions", "gpt-5.2"))
        assertNull(Reasoning.known(4, "https://api.openai.com/v1/responses", "gpt-4o"))
        assertNotNull(Reasoning.known(5, "https://api.openai.com/v1/chat/completions", "gpt-5.2"))
    }
    @Test fun ollamaHttpIsLocalOnly() {
        assertTrue(ModelDiscovery.isAllowedEndpoint("http://192.168.1.20:11434/api/chat", 6))
        assertFalse(ModelDiscovery.isAllowedEndpoint("http://192.168.1.20.evil.com:11434/api/chat", 6))
        assertFalse(ModelDiscovery.isAllowedEndpoint("http://8.8.8.8/api/chat", 6))
        assertFalse(ModelDiscovery.isAllowedEndpoint("http://192.168.1.20:11434/api/chat", 5))
    }
}
