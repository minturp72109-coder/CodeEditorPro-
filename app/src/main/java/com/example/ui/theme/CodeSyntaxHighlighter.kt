package com.example.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.util.regex.Pattern

class CodeVisualTransformation(
    private val extension: String,
    private val isDarkTheme: Boolean
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = highlightCode(text.text, extension, isDarkTheme)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

fun highlightCode(text: String, extension: String, isDark: Boolean): AnnotatedString {
    return buildAnnotatedString {
        append(text)

        val ext = extension.lowercase()
        if (text.isEmpty()) return@buildAnnotatedString

        // Set Default Styles based on light/dark theme
        val defaultColor = if (isDark) Color(0xFFABB2BF) else Color(0xFF383A42)
        addStyle(SpanStyle(color = defaultColor, fontFamily = FontFamily.Monospace), 0, text.length)

        // Palette definitions
        val commentColor = if (isDark) Color(0xFF5C6370) else Color(0xFF9E9E9E)
        val keywordColor = if (isDark) Color(0xFFC678DD) else Color(0xFF0000FF)
        val stringColor = if (isDark) Color(0xFF98C379) else Color(0xFF098658)
        val tagColor = if (isDark) Color(0xFFE06C75) else Color(0xFFA31515)
        val attrColor = if (isDark) Color(0xFFD19A66) else Color(0xFF0451A5)
        val numberColor = if (isDark) Color(0xFFD19A66) else Color(0xFF098658)
        val selectorColor = if (isDark) Color(0xFF61AFEF) else Color(0xFF0000FF)

        when (ext) {
            "html", "xml" -> {
                // Comments: <!-- ... -->
                val commentPattern = Pattern.compile("<!--[\\s\\S]*?-->")
                val commentMatcher = commentPattern.matcher(text)
                while (commentMatcher.find()) {
                    addStyle(SpanStyle(color = commentColor, fontWeight = FontWeight.Normal), commentMatcher.start(), commentMatcher.end())
                }

                // Tags: <tag_name ...> or </tag_name>
                val tagPattern = Pattern.compile("<[^>]+>")
                val tagMatcher = tagPattern.matcher(text)
                while (tagMatcher.find()) {
                    val tagStart = tagMatcher.start()
                    val tagEnd = tagMatcher.end()
                    addStyle(SpanStyle(color = tagColor), tagStart, tagEnd)

                    // Parse strings and attributes inside tags
                    val tagSubstring = text.substring(tagStart, tagEnd)
                    
                    // Highlight strings inside tag attributes
                    val strPattern = Pattern.compile("\"[^\"]*\"|'[^']*'")
                    val strMatcher = strPattern.matcher(tagSubstring)
                    while (strMatcher.find()) {
                        addStyle(SpanStyle(color = stringColor), tagStart + strMatcher.start(), tagStart + strMatcher.end())
                    }

                    // Highlight tag attributes (e.g. href=, src=, class=, style=)
                    val attrPattern = Pattern.compile("\\b([a-zA-Z0-9_-]+)(?=\\s*=)")
                    val attrMatcher = attrPattern.matcher(tagSubstring)
                    while (attrMatcher.find()) {
                        addStyle(SpanStyle(color = attrColor), tagStart + attrMatcher.start(), tagStart + attrMatcher.end())
                    }
                }
            }
            "css" -> {
                // Comments: /* ... */
                val commentPattern = Pattern.compile("/\\*[\\s\\S]*?\\*/")
                val commentMatcher = commentPattern.matcher(text)
                while (commentMatcher.find()) {
                    addStyle(SpanStyle(color = commentColor), commentMatcher.start(), commentMatcher.end())
                }

                // Selectors: .class, #id, tag, element {
                val selectorPattern = Pattern.compile("[.#a-zA-Z0-9_-]+\\s*(?=\\{)")
                val selectorMatcher = selectorPattern.matcher(text)
                while (selectorMatcher.find()) {
                    addStyle(SpanStyle(color = selectorColor, fontWeight = FontWeight.Bold), selectorMatcher.start(), selectorMatcher.end())
                }

                // Property keys: color, background, padding (followed by :)
                val keyPattern = Pattern.compile("\\b([a-zA-Z-]+)\\s*(?=:)")
                val keyMatcher = keyPattern.matcher(text)
                while (keyMatcher.find()) {
                    addStyle(SpanStyle(color = attrColor), keyMatcher.start(), keyMatcher.end())
                }

                // Property values: red, 12px, #fff (after : and before ;)
                val valPattern = Pattern.compile(":\\s*([^;]+)\\s*;")
                val valMatcher = valPattern.matcher(text)
                while (valMatcher.find()) {
                    addStyle(SpanStyle(color = stringColor), valMatcher.start(1), valMatcher.end(1))
                }
            }
            "js", "json" -> {
                // Single line comments: // ...
                val singleCommentPattern = Pattern.compile("//.*")
                val scMatcher = singleCommentPattern.matcher(text)
                while (scMatcher.find()) {
                    addStyle(SpanStyle(color = commentColor), scMatcher.start(), scMatcher.end())
                }

                // Multi-line comments: /* ... */
                val multiCommentPattern = Pattern.compile("/\\*[\\s\\S]*?\\*/")
                val mcMatcher = multiCommentPattern.matcher(text)
                while (mcMatcher.find()) {
                    addStyle(SpanStyle(color = commentColor), mcMatcher.start(), mcMatcher.end())
                }

                // Keywords
                val keywords = listOf(
                    "const", "let", "var", "function", "return", "if", "else", 
                    "for", "while", "do", "switch", "case", "break", "continue", 
                    "class", "import", "export", "from", "new", "this", "true", 
                    "false", "null", "undefined", "try", "catch", "async", "await"
                )
                val keywordRegex = "\\b(" + keywords.joinToString("|") + ")\\b"
                val keywordPattern = Pattern.compile(keywordRegex)
                val keywordMatcher = keywordPattern.matcher(text)
                while (keywordMatcher.find()) {
                    addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold), keywordMatcher.start(), keywordMatcher.end())
                }

                // Strings: "..." or '...' or `...`
                val stringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'|`[^`]*`|\\\\\"[^\\\\\"]*\\\\\"")
                val stringMatcher = stringPattern.matcher(text)
                while (stringMatcher.find()) {
                    addStyle(SpanStyle(color = stringColor), stringMatcher.start(), stringMatcher.end())
                }

                // Numbers
                val numberPattern = Pattern.compile("\\b(\\d+(\\.\\d+)?)\\b")
                val numberMatcher = numberPattern.matcher(text)
                while (numberMatcher.find()) {
                    addStyle(SpanStyle(color = numberColor), numberMatcher.start(), numberMatcher.end())
                }
            }
        }
    }
}
