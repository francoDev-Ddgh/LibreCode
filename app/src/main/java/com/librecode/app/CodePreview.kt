package com.librecode.app

object CodePreview {
    fun lines(code: String): List<String> {
        if (code.isEmpty()) return emptyList()
        val text = if (code.endsWith('\n')) code.dropLast(1) else code
        return text.split('\n')
    }
    fun needsCollapse(code: String): Boolean = lines(code).size > 25
    fun preview(code: String): String = if (needsCollapse(code)) lines(code).take(15).joinToString("\n") else code
}
