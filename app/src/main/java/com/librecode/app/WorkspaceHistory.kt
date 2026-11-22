package com.librecode.app

/** Chat keeps the original file so upgrading never moves or overwrites the previous history. */
object WorkspaceHistory {
    fun fileName(workspace: String): String = if (workspace == "work") "chats_work.json" else "chats.json"
}
