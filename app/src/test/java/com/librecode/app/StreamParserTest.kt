package com.librecode.app

import org.junit.Assert.*
import org.junit.Test

class StreamParserTest {
    @Test fun chatStreamIgnoresThinking() {
        val chunk = StreamParser.decode(5, """{"choices":[{"delta":{"reasoning_content":"secret","content":"Hola"}}]}""")
        assertEquals("Hola", chunk.text)
        assertFalse(chunk.done)
        assertTrue(StreamParser.decode(5, "[DONE]").done)
    }
    @Test fun anthropicAndGoogleEvents() {
        assertEquals("Hola", StreamParser.decode(1, """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hola"}}""").text)
        assertEquals("", StreamParser.decode(1, """{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"secret"}}""").text)
        assertTrue(StreamParser.decode(1, """{"type":"message_stop"}""").done)
        val google = StreamParser.decode(0, """{"candidates":[{"content":{"parts":[{"thought":true,"text":"secret"},{"text":"Sí"}]},"finishReason":"STOP"}]}""")
        assertEquals("Sí", google.text); assertTrue(google.done)
    }
    @Test fun responsesAndOllama() {
        assertEquals("Texto", StreamParser.decode(4, """{"type":"response.output_text.delta","delta":"Texto"}""").text)
        assertTrue(StreamParser.decode(4, """{"type":"response.completed"}""").done)
        assertEquals("Hola", StreamParser.decode(6, """{"message":{"content":"Hola","thinking":"secret"},"done":false}""").text)
        assertTrue(StreamParser.decode(6, """{"done":true}""").done)
    }
    @Test fun serverErrorsAreRecognized() {
        assertNotNull(StreamParser.decode(5, """{"error":{"message":"incorrect key"}}""").error)
    }
}
