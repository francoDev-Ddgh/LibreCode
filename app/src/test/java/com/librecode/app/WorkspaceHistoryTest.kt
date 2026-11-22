package com.librecode.app

import org.junit.Assert.*
import org.junit.Test

class WorkspaceHistoryTest {
    @Test fun chatKeepsThePreviousHistoryFile() {
        assertEquals("chats.json", WorkspaceHistory.fileName("chat"))
    }
    @Test fun workUsesAnIndependentFile() {
        assertEquals("chats_work.json", WorkspaceHistory.fileName("work"))
        assertNotEquals(WorkspaceHistory.fileName("chat"), WorkspaceHistory.fileName("work"))
    }
}
