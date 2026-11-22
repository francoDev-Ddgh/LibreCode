package com.librecode.app

import org.junit.Assert.*
import org.junit.Test

class CodePreviewTest {
    private fun sample(n: Int) = (1..n).joinToString("\n") { "line $it" } + "\n"
    @Test fun twentyFiveLinesRemainExpanded() {
        val code = sample(25)
        assertFalse(CodePreview.needsCollapse(code))
        assertEquals(code, CodePreview.preview(code))
    }
    @Test fun overTwentyFiveShowsFirstFifteen() {
        val code = sample(26)
        assertTrue(CodePreview.needsCollapse(code))
        assertEquals(15, CodePreview.lines(CodePreview.preview(code)).size)
        assertTrue(CodePreview.preview(code).endsWith("line 15"))
        assertEquals(26, CodePreview.lines(code).size)
    }
    @Test fun blankLinesAreCountedWithoutInventingTrailingLine() {
        assertEquals(listOf("one", "", "three"), CodePreview.lines("one\n\nthree\n"))
        assertEquals(emptyList<String>(), CodePreview.lines(""))
    }
}
