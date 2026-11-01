package com.jms.pssoundboard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Small hand-drawn icon set for the playful look (24 x 24 grid). Tint them with Icon's tint;
 * the black here is only a placeholder colour.
 */
object PlayfulIcons {
    private fun icon(name: String, filled: List<String> = emptyList(), stroked: List<String> = emptyList()) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            filled.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
            stroked.forEach {
                addPath(
                    addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2.2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
        "M${cx - rx} ${cy}a$rx $ry 0 1 0 ${2 * rx} 0a$rx $ry 0 1 0 ${-2 * rx} 0z"

    val Deck = icon(
        "deck",
        filled = listOf(ellipse(7.5f, 7.5f, 4.5f, 4f), ellipse(16.5f, 7.5f, 4f, 4.5f), ellipse(7.5f, 16.5f, 4f, 4.5f), ellipse(16.5f, 16.5f, 4.5f, 4f)),
    )
    val MiniDeck = icon(
        "mini_deck",
        filled = listOf(ellipse(7f, 8f, 3.2f, 3.2f), ellipse(16.5f, 7f, 2.6f, 2.6f), ellipse(9f, 16.5f, 2.6f, 2.6f), ellipse(17f, 16f, 3.4f, 3.4f)),
    )
    val Library = icon("library", stroked = listOf(ellipse(11f, 11f, 6.5f, 6.5f), "M20 20l-4-4"))
    val Voice = icon(
        "voice",
        stroked = listOf("M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z", "M5.5 11a6.5 6.5 0 0 0 13 0", "M12 17.5V21"),
    )
    val Vault = icon(
        "vault",
        stroked = listOf("M5 9h14l-1.2 10.5a1.5 1.5 0 0 1-1.5 1.5H7.7a1.5 1.5 0 0 1-1.5-1.5z", "M8 9V7a4 4 0 0 1 8 0v2"),
    )
    val Setup = icon(
        "setup",
        stroked = listOf("M4 7h10M18 7h2M4 17h4M12 17h8", ellipse(16f, 7f, 2f, 2f), ellipse(10f, 17f, 2f, 2f)),
    )
    val Plus = icon("plus", stroked = listOf("M12 5v14M5 12h14"))
    val Edit = icon("edit", stroked = listOf("M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"))
    val Stop = icon("stop", filled = listOf("M8 4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8a4 4 0 0 1 4-4z"))
    val Star = icon("star", filled = listOf("M12 2.5 15 9l7 .7-5.3 4.7 1.6 7L12 17.8 5.7 21.4l1.6-7L2 9.7 9 9z"))
    val Play = icon("play", filled = listOf("M8 5.5v13l10.5-6.5z"))
    val More = icon("more", filled = listOf(ellipse(5f, 12f, 2f, 2f), ellipse(12f, 12f, 2f, 2f), ellipse(19f, 12f, 2f, 2f)))
    val Close = icon("close", stroked = listOf("M6 6l12 12M18 6 6 18"))
    val Check = icon("check", stroked = listOf("M5 12.5l4.5 4.5L19 7.5"))
    val Speaker = icon("speaker", stroked = listOf("M11 5 6 9H2v6h4l5 4z", "M15.5 8.5a5 5 0 0 1 0 7"))
    val Download = icon("download", stroked = listOf("M12 4v11", "M7 10.5l5 5 5-5", "M5 20h14"))
    val Share = icon(
        "share",
        stroked = listOf(
            "M8.5 13.5l7 4M15.5 6.5l-7 4",
            ellipse(18f, 5f, 2.5f, 2.5f),
            ellipse(6f, 12f, 2.5f, 2.5f),
            ellipse(18f, 19f, 2.5f, 2.5f),
        ),
    )
    val Copy = icon("copy", stroked = listOf("M9 9h10v11H9z", "M5 15V4h10"))
    val Trash = icon("trash", stroked = listOf("M4 7h16", "M9 7V4h6v3", "M6.5 7l1 13h9l1-13"))
    val Mic = Voice
    val Folder = icon("folder", stroked = listOf("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"))
    val Link = icon("link", stroked = listOf("M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"))
    val ChevronDown = icon("chevron_down", stroked = listOf("M6 9l6 6 6-6"))
    val Feedback = icon("feedback", stroked = listOf("M5 5h14a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H10l-5 4v-4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M9 10h6"))
}
