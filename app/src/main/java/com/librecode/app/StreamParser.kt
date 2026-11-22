package com.librecode.app

import org.json.JSONObject

data class StreamPiece(val text: String = "", val done: Boolean = false, val error: String? = null, val reasoningTokens: Int? = null)

object StreamParser {
    fun decode(type: Int, data: String): StreamPiece {
        if (data.trim() == "[DONE]") return StreamPiece(done = true)
        val obj = JSONObject(data)
        if (obj.has("error")) {
            val error = obj.opt("error")
            return StreamPiece(error = if (error is JSONObject) error.optString("message", "Error del servidor") else error.toString())
        }
        return when (type) {
            0 -> {
                val candidate = obj.optJSONArray("candidates")?.optJSONObject(0)
                val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
                val content = buildString { if (parts != null) for (i in 0 until parts.length()) { val p = parts.optJSONObject(i) ?: continue; if (!p.optBoolean("thought")) append(p.optString("text", "")) } }
                val done = !candidate?.optString("finishReason").isNullOrBlank()
                val usage = obj.optJSONObject("usageMetadata")
                StreamPiece(content, done, reasoningTokens = usage?.takeIf { it.has("thoughtsTokenCount") }?.optInt("thoughtsTokenCount"))
            }
            1 -> {
                val event = obj.optString("type")
                val delta = obj.optJSONObject("delta")
                val content = if (event == "content_block_delta" && delta?.optString("type") == "text_delta") delta.optString("text", "") else ""
                val usage = obj.optJSONObject("usage")?.optJSONObject("output_tokens_details")
                StreamPiece(content, event == "message_stop", reasoningTokens = usage?.takeIf { it.has("thinking_tokens") }?.optInt("thinking_tokens"))
            }
            4 -> {
                val event = obj.optString("type")
                if (event == "response.failed" || event == "response.incomplete" || event == "error") return StreamPiece(error = "La respuesta no se completó ($event).")
                val usage = obj.optJSONObject("response")?.optJSONObject("usage")?.optJSONObject("output_tokens_details")
                StreamPiece(if (event == "response.output_text.delta") obj.optString("delta", "") else "", event == "response.completed", reasoningTokens = usage?.takeIf { it.has("reasoning_tokens") }?.optInt("reasoning_tokens"))
            }
            6 -> StreamPiece(obj.optJSONObject("message")?.optString("content", "") ?: "", obj.optBoolean("done"))
            else -> {
                val choice = obj.optJSONArray("choices")?.optJSONObject(0)
                val usage = obj.optJSONObject("usage")?.optJSONObject("completion_tokens_details")
                StreamPiece(choice?.optJSONObject("delta")?.optString("content", "") ?: "", false, reasoningTokens = usage?.takeIf { it.has("reasoning_tokens") }?.optInt("reasoning_tokens"))
            }
        }
    }
}
