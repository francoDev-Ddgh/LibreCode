package com.librecode.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.noties.markwon.Markwon
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Document
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node
import org.commonmark.node.OrderedList

/** Renders native Markdown blocks while providing independent code cards. */
class ChatMarkdownRenderer(private val context: Context, private val markwon: Markwon) {
    private val ink = Color.rgb(44, 42, 37)
    private val muted = Color.rgb(115, 110, 100)
    private val accent = Color.rgb(195, 101, 72)
    private fun dp(n: Int) = (context.resources.displayMetrics.density * n).toInt()
    private fun background(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun label(value: String, size: Float, color: Int = ink) = TextView(context).apply { text = value; textSize = size; setTextColor(color) }
    private fun containsCode(node: Node): Boolean {
        if (node is FencedCodeBlock || node is IndentedCodeBlock) return true
        var child = node.firstChild
        while (child != null) { if (containsCode(child)) return true; child = child.next }
        return false
    }
    fun render(parent: LinearLayout, markdown: String) {
        val document = markwon.parse(markdown)
        renderChildren(parent, document)
    }
    private fun renderChildren(parent: LinearLayout, node: Node) {
        var child = node.firstChild
        while (child != null) {
            renderNode(parent, child)
            child = child.next
        }
    }
    private fun renderNode(parent: LinearLayout, node: Node) {
        when (node) {
            is FencedCodeBlock -> addCode(parent, node.literal, node.info?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty())
            is IndentedCodeBlock -> addCode(parent, node.literal, "")
            is BulletList, is OrderedList -> if (containsCode(node)) {
                var item = node.firstChild
                var number = 1
                while (item != null) {
                    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4)) }
                    row.addView(label(if (node is OrderedList) "${number++}." else "•", 15f, muted), LinearLayout.LayoutParams(dp(22), -2))
                    val nested = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                    row.addView(nested, LinearLayout.LayoutParams(0, -2, 1f)); parent.addView(row)
                    renderChildren(nested, item)
                    item = item.next
                }
            } else addText(parent, node)
            is BlockQuote -> if (containsCode(node)) {
                val quote = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(4), dp(4)); background = background(Color.rgb(244, 242, 236), 10) }
                parent.addView(quote, LinearLayout.LayoutParams(-1, -2)); renderChildren(quote, node)
            } else addText(parent, node)
            is Document -> renderChildren(parent, node)
            else -> addText(parent, node)
        }
    }
    private fun addText(parent: LinearLayout, node: Node) {
        val view = label("", 16f).apply { setTextIsSelectable(true); setLineSpacing(dp(4).toFloat(), 1f) }
        markwon.setParsedMarkdown(view, markwon.render(node))
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }
    private fun addCode(parent: LinearLayout, code: String, language: String) {
        val count = CodePreview.lines(code).size
        val collapsible = CodePreview.needsCollapse(code)
        val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; background = background(Color.rgb(237, 235, 229), 14) }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(8), dp(10), dp(8)) }
        val name = (language.take(24).ifBlank { "Código" })
        header.addView(label(name, 12f, muted).apply { maxLines = 1; typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        val copy = label("Copiar", 13f, accent).apply {
            gravity = Gravity.CENTER; setPadding(dp(14), dp(9), dp(14), dp(9)); background = background(Color.WHITE, 10)
            contentDescription = "Copiar código completo"
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Código", code))
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                text = "Copiado"; postDelayed({ text = "Copiar" }, 1600)
            }
        }
        header.addView(copy); card.addView(header)
        val horizontal = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = true; isFillViewport = true }
        val codeView = label(if (collapsible) CodePreview.preview(code) else code, 13.5f).apply {
            typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setHorizontallyScrolling(true)
            setPadding(dp(14), dp(8), dp(14), dp(12)); setLineSpacing(dp(3).toFloat(), 1f)
        }
        horizontal.addView(codeView, HorizontalScrollView.LayoutParams(-2, -2)); card.addView(horizontal, LinearLayout.LayoutParams(-1, -2))
        if (collapsible) {
            var expanded = false
            val toggle = label("Mostrar todo · $count líneas", 13f, accent).apply {
                setPadding(dp(14), dp(12), dp(14), dp(14))
                setOnClickListener {
                    expanded = !expanded
                    codeView.text = if (expanded) code else CodePreview.preview(code)
                    text = if (expanded) "Mostrar menos" else "Mostrar todo · $count líneas"
                    horizontal.scrollTo(0, 0)
                }
            }
            card.addView(toggle, LinearLayout.LayoutParams(-1, -2))
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(12) })
    }
}
