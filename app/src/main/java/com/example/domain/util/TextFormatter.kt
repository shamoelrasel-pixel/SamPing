package com.example.domain.util

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object TextFormatter {

    fun toUnicodeBold(char: Char): String {
        return when (char) {
            in 'A'..'Z' -> String(Character.toChars(0x1D5D4 + (char - 'A'))) // Sans-serif bold
            in 'a'..'z' -> String(Character.toChars(0x1D5EE + (char - 'a')))
            in '0'..'9' -> String(Character.toChars(0x1D7EC + (char - '0')))
            else -> char.toString()
        }
    }

    fun toUnicodeItalic(char: Char): String {
        return when (char) {
            in 'A'..'Z' -> String(Character.toChars(0x1D608 + (char - 'A'))) // Sans-serif italic
            in 'a'..'z' -> String(Character.toChars(0x1D622 + (char - 'a')))
            else -> char.toString()
        }
    }

    fun toUnicodeUnderline(char: Char): String {
        return if (char.isWhitespace()) {
            char.toString()
        } else {
            "$char\u0332"
        }
    }

    fun clearFormatting(text: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            if (codePoint == 0x0332) {
                // Skip combining low line
                i += charCount
                continue
            }

            val plainChar = when {
                // Sans-serif Bold Upper
                codePoint in 0x1D5D4..0x1D5ED -> ('A' + (codePoint - 0x1D5D4)).toString()
                // Sans-serif Bold Lower
                codePoint in 0x1D5EE..0x1D607 -> ('a' + (codePoint - 0x1D5EE)).toString()
                // Sans-serif Bold Digits
                codePoint in 0x1D7EC..0x1D7F5 -> ('0' + (codePoint - 0x1D7EC)).toString()
                // Serif Bold Upper
                codePoint in 0x1D400..0x1D419 -> ('A' + (codePoint - 0x1D400)).toString()
                // Serif Bold Lower
                codePoint in 0x1D41A..0x1D433 -> ('a' + (codePoint - 0x1D41A)).toString()
                // Serif Bold Digits
                codePoint in 0x1D7CE..0x1D7D7 -> ('0' + (codePoint - 0x1D7CE)).toString()
                // Sans-serif Italic Upper
                codePoint in 0x1D608..0x1D621 -> ('A' + (codePoint - 0x1D608)).toString()
                // Sans-serif Italic Lower
                codePoint in 0x1D622..0x1D63B -> ('a' + (codePoint - 0x1D622)).toString()
                // Serif Italic Upper
                codePoint in 0x1D434..0x1D44D -> ('A' + (codePoint - 0x1D434)).toString()
                // Serif Italic Lower
                codePoint in 0x1D44E..0x1D467 -> ('a' + (codePoint - 0x1D44E)).toString()
                else -> String(Character.toChars(codePoint))
            }
            result.append(plainChar)
            i += charCount
        }
        return result.toString()
    }

    fun applyBold(currentValue: TextFieldValue): TextFieldValue {
        return applyTransform(currentValue) { text ->
            val plain = clearFormatting(text)
            plain.map { toUnicodeBold(it) }.joinToString("")
        }
    }

    fun applyItalic(currentValue: TextFieldValue): TextFieldValue {
        return applyTransform(currentValue) { text ->
            val plain = clearFormatting(text)
            plain.map { toUnicodeItalic(it) }.joinToString("")
        }
    }

    fun applyUnderline(currentValue: TextFieldValue): TextFieldValue {
        return applyTransform(currentValue) { text ->
            val plain = clearFormatting(text)
            plain.map { toUnicodeUnderline(it) }.joinToString("")
        }
    }

    fun applyClear(currentValue: TextFieldValue): TextFieldValue {
        return applyTransform(currentValue) { text ->
            clearFormatting(text)
        }
    }

    private fun applyTransform(
        currentValue: TextFieldValue,
        transform: (String) -> String
    ): TextFieldValue {
        val text = currentValue.text
        val selection = currentValue.selection

        return if (selection.collapsed || selection.start == selection.end) {
            // Apply to the entire text
            val newText = transform(text)
            TextFieldValue(
                text = newText,
                selection = TextRange(newText.length)
            )
        } else {
            // Apply only to selected portion
            val start = minOf(selection.start, selection.end)
            val end = maxOf(selection.start, selection.end)
            val before = text.substring(0, start)
            val target = text.substring(start, end)
            val after = text.substring(end)
            val transformed = transform(target)
            val newText = before + transformed + after
            TextFieldValue(
                text = newText,
                selection = TextRange(start, start + transformed.length)
            )
        }
    }
}

@Composable
fun TextFormattingToolbar(
    modifier: Modifier = Modifier,
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormattingButton(
                label = "B",
                fontWeight = FontWeight.Black,
                contentDescription = "Bold Formatting",
                onClick = onBoldClick
            )

            FormattingButton(
                label = "I",
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                contentDescription = "Italic Formatting",
                onClick = onItalicClick
            )

            FormattingButton(
                label = "U",
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold,
                contentDescription = "Underline Formatting",
                onClick = onUnderlineClick
            )

            FormattingButton(
                label = "Tx",
                fontWeight = FontWeight.Medium,
                contentDescription = "Clear Formatting",
                onClick = onClearClick
            )
        }
    }
}

@Composable
private fun FormattingButton(
    label: String,
    onClick: () -> Unit,
    contentDescription: String,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    textDecoration: TextDecoration = TextDecoration.None
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
