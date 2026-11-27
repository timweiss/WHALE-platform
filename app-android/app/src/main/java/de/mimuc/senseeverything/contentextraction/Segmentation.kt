package de.mimuc.senseeverything.contentextraction

/* Port of segment.py, anchor mode with offset "separator" and fallback "whole" only. */

/** A row's signature: node types in its subtree (CONTAINER excluded) and its height bucket. */
class Sig(
    val types: Set<String>,
    /** null when the row is clipped (its height is meaningless) */
    val hb: Int?
) {
    /** Same text as the harness's Sig.__str__ (debug only). */
    override fun toString(): String {
        val t = types.map { it.take(4) }.sorted().joinToString("+").ifEmpty { "-" }
        return "$t/${hb ?: "?"}"
    }
}

fun touchesTop(n: Node, main: Node, p: Params): Boolean = n.y <= main.y + p.edgeEps

fun touchesBottom(n: Node, main: Node, p: Params): Boolean = n.bottom >= main.bottom - p.edgeEps

fun signature(row: Node, main: Node, p: Params): Sig {
    val types = iterNodes(row).map { it.type }.filter { it != "CONTAINER" }.toSet()
    if (touchesTop(row, main, p) || touchesBottom(row, main, p)) return Sig(types, null)
    return Sig(types, p.heightBuckets.count { row.h >= it })
}

fun sigMatch(a: Sig, b: Sig, p: Params): Boolean {
    if (a.hb != null && b.hb != null && a.hb != b.hb) return false
    val union = a.types union b.types
    return union.isEmpty() || (a.types intersect b.types).size.toDouble() / union.size >= p.typeJaccard
}

/** A thin row without any text: divider/spacer between items. */
fun isSeparator(row: Node, p: Params): Boolean = row.h < p.heightBuckets[0] && !hasLabel(row)

/**
 * Index of the row whose signature marks the start of every item, or null.
 *
 * Candidates: rows with known height (not at an edge) and some text, whose signature matches at
 * least one other row on screen. Header and footer both repeat with two posts in view, so the
 * candidate right after a separator row wins; otherwise the earliest candidate.
 */
fun chooseAnchor(rows: List<Node>, sigs: List<Sig>, p: Params): Int? {
    val cands = rows.indices.filter { i ->
        sigs[i].hb != null && hasLabel(rows[i]) && sigs.count { t -> sigMatch(sigs[i], t, p) } >= 2
    }
    if (cands.isEmpty()) return null
    // The row after a separator may itself be clipped (no candidate), so match by signature.
    for (j in 1 until rows.size) {
        if (isSeparator(rows[j - 1], p)) {
            for (i in cands) {
                if (sigMatch(sigs[i], sigs[j], p)) return i
            }
        }
    }
    return cands[0]
}

/** Split rows into groups; every index in [starts] begins a new group. */
fun splitBefore(rows: List<Node>, starts: Set<Int>): List<List<Node>> {
    val groups = mutableListOf<MutableList<Node>>()
    rows.forEachIndexed { i, r ->
        if (groups.isEmpty() || i in starts) groups.add(mutableListOf())
        groups.last().add(r)
    }
    return groups
}

fun buildScreen(root: Node, p: Params = Params()): Screen {
    var main = findMainContainer(root, p)
    // A main container without any text while text exists elsewhere (e.g. an empty list behind a
    // full-screen post): treat the whole screen as content instead of dropping it into chrome.
    val mainFallback = p.emptyMainFallback && main !== root && !hasLabel(main) && hasLabel(root)
    if (mainFallback) main = root
    val rows = contentRows(main, p)
    val sigs = rows.map { signature(it, main, p) }

    val chrome = mutableListOf<Entry>()
    if (main !== root) {
        collectEntries(root, p, chrome, skip = setOf(main.id))
    }

    var mode = SegmentationMode.ANCHOR
    val anchorIndex = chooseAnchor(rows, sigs, p)
    var groups: List<List<Node>>? = null
    if (anchorIndex != null) {
        val starts = sigs.indices.filter { sigMatch(sigs[anchorIndex], sigs[it], p) }.toSet()
        groups = splitBefore(rows, starts)
    }
    if (groups == null) {
        mode = SegmentationMode.WHOLE
        groups = if (rows.isNotEmpty()) listOf(rows) else emptyList()
    }

    val segments = groups.mapNotNull { g ->
        val entries = mutableListOf<Entry>()
        for (r in g) collectEntries(r, p, entries)
        // separators / empty media placeholders carry nothing for the model
        if (entries.isEmpty()) null
        else Segment(
            entries = entries,
            clippedTop = touchesTop(g.first(), main, p),
            clippedBottom = touchesBottom(g.last(), main, p),
            rowIds = g.map { it.id }
        )
    }

    return Screen(
        mode = mode,
        chrome = chrome,
        segments = segments,
        mainId = main.id,
        mainFallback = mainFallback,
        anchorIndex = anchorIndex,
        anchor = anchorIndex?.let { sigs[it].toString() },
        rowSignatures = sigs.map { it.toString() }
    )
}
