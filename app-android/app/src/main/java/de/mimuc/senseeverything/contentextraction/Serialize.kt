package de.mimuc.senseeverything.contentextraction

/*
 * Port of serialize.py with geometry "rows". The output is the screen part of the prompt:
 *
 *     SCREEN CHROME
 *     1 Entdecken
 *     2 desc: Zurück
 *     CONTENT
 *     --- segment 1 [clipped top]
 *     3 klarna Verwalte deine Finanzen mit Klarna.
 *     --- segment 2
 *     4 desc: Gefällt mir · 99 · desc: Kommentieren · 7
 *
 * Visible text is written bare, contentDescription after "desc:". Entries side by side on one visual
 * line share a line, joined by " · ". Lines are numbered across the whole screen. In "whole" mode
 * there are no segment headers; clipping is shown as standalone [clipped top]/[clipped bottom] lines.
 */

fun formatEntry(e: Entry): String {
    if (e.text != null && e.desc != null) return "${e.text} | desc: ${e.desc}"
    return e.text ?: "desc: ${e.desc}"
}

/** b is to the right of a and overlaps it vertically by enough of the smaller height. */
fun sameLine(a: Entry, b: Entry, p: Params): Boolean {
    val overlap = minOf(a.bottom, b.bottom) - maxOf(a.y, b.y)
    return b.x >= a.right - p.edgeEps && overlap >= p.sameLineOverlap * minOf(a.h, b.h)
}

fun groupLines(entries: List<Entry>, p: Params): List<List<Entry>> {
    val lines = mutableListOf<MutableList<Entry>>()
    for (e in entries) {
        if (lines.isNotEmpty() && sameLine(lines.last().last(), e, p)) {
            lines.last().add(e)
        } else {
            lines.add(mutableListOf(e))
        }
    }
    return lines
}

private fun clipMarks(seg: Segment): String =
    listOfNotNull(
        "[clipped top]".takeIf { seg.clippedTop },
        "[clipped bottom]".takeIf { seg.clippedBottom }
    ).joinToString(" ")

/**
 * Whole screen, or only the given segments (0-based) plus chrome, for one call per segment. Line
 * numbers stay the same as in the full serialization, so they can be compared across calls.
 */
fun serialize(screen: Screen, p: Params, segmentIndices: Set<Int>? = null): String {
    val out = mutableListOf<String>()
    var k = 0

    fun emit(entries: List<Entry>, show: Boolean = true) {
        for (line in groupLines(entries, p)) {
            k += 1 // counted even when hidden, to keep global numbering stable
            if (show) out.add("$k " + line.joinToString(" · ") { formatEntry(it) })
        }
    }

    if (screen.chrome.isNotEmpty()) {
        out.add("SCREEN CHROME")
        emit(screen.chrome)
    }
    out.add("CONTENT")
    val whole = screen.mode == SegmentationMode.WHOLE
    screen.segments.forEachIndexed { i, seg ->
        val selected = segmentIndices == null || i in segmentIndices
        if (whole) {
            if (seg.clippedTop) out.add("[clipped top]")
        } else if (selected) {
            out.add("--- segment ${i + 1} ${clipMarks(seg)}".trimEnd())
        }
        emit(seg.entries, selected)
        if (whole && seg.clippedBottom) out.add("[clipped bottom]")
    }
    return out.joinToString("\n")
}
