package de.mimuc.senseeverything.contentextraction

import java.math.BigDecimal
import java.math.RoundingMode

/*
 * Port of preprocess.py: label cleaning, main container, rows, flattening. App-agnostic.
 *
 * Three Python semantics are reproduced explicitly, since the Kotlin/Java defaults differ:
 * - Python's \s and str.strip() use str.isspace() (29 code points incl. U+00A0); Java's \s is ASCII only.
 * - Python's \w is every Unicode letter and number plus "_"; Java's \w is ASCII only.
 * - Python's len() counts code points; Kotlin's length counts UTF-16 units (an emoji is 2).
 */

/** Exactly the characters for which Python's str.isspace() is true (checked against CPython 3.14). */
internal fun isPythonWhitespace(c: Char): Boolean = when (c.code) {
    in 0x09..0x0d, in 0x1c..0x20, 0x85, 0xa0, 0x1680, in 0x2000..0x200a,
    0x2028, 0x2029, 0x202f, 0x205f, 0x3000 -> true
    else -> false
}

/** Python's \w for str patterns: Unicode categories L* and N*, plus "_". */
internal fun isPythonWordChar(codePoint: Int): Boolean {
    if (codePoint == '_'.code) return true
    return when (Character.getType(codePoint).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER,
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }
}

/** `re.sub(r"\s+", " ", s).strip()`, null when nothing is left. */
fun clean(s: String?): String? {
    if (s == null) return null
    val out = StringBuilder(s.length)
    var pendingSpace = false
    for (c in s) {
        if (isPythonWhitespace(c)) {
            pendingSpace = true
        } else {
            // a run of whitespace becomes one space; leading and trailing runs vanish (strip)
            if (pendingSpace && out.isNotEmpty()) out.append(' ')
            pendingSpace = false
            out.append(c)
        }
    }
    return if (out.isEmpty()) null else out.toString()
}

/** Stack-based pre-order walk in the harness's order (last child first); `max` ties depend on it. */
fun iterNodes(node: Node): Sequence<Node> = sequence {
    val stack = ArrayDeque<Node>()
    stack.addLast(node)
    while (stack.isNotEmpty()) {
        val n = stack.removeLast()
        yield(n)
        stack.addAll(n.children)
    }
}

/**
 * Python's round(v, 2): the exact binary value rounded half-even to two decimals, then the nearest
 * double. Parsing the decimal string keeps the last step correctly rounded on every runtime.
 */
internal fun round2(v: Double): Double =
    BigDecimal(v).setScale(2, RoundingMode.HALF_EVEN).toPlainString().toDouble()

/** Python float comparison: -0.0 == 0.0, unlike Double.compareTo. */
internal fun pyCompare(a: Double, b: Double): Int = if (a < b) -1 else if (a > b) 1 else 0

/** Sorted by the harness's reading key (round(y, 2), x); stable, like Python's sort. */
fun sortedReading(nodes: List<Node>): List<Node> =
    nodes.map { it to round2(it.y) }
        .sortedWith { (a, ay), (b, by) -> pyCompare(ay, by).takeIf { it != 0 } ?: pyCompare(a.x, b.x) }
        .map { it.first }

/** Python's max(..., key=...): the first element with the largest key, compared with ">". */
private fun firstMaxBy(nodes: List<Node>, key: (Node) -> Double): Node {
    var best = nodes.first()
    var bestKey = key(best)
    for (n in nodes.drop(1)) {
        val k = key(n)
        if (k > bestKey) {
            best = n
            bestKey = k
        }
    }
    return best
}

/**
 * Largest scrollable, then repeatedly the largest nested scrollable that still covers >= nestRatio
 * of it (an outer tab pager wrapping the actual feed/reel pager). Without any scrollable the whole
 * screen is content.
 */
fun findMainContainer(root: Node, p: Params): Node {
    val scrollables = iterNodes(root).filter { it.scrollable }.toList()
    if (scrollables.isEmpty()) return root
    var main = firstMaxBy(scrollables) { it.area }
    while (true) {
        val current = main
        val nested = iterNodes(current)
            .filter { it !== current && it.scrollable && it.area >= p.nestRatio * current.area }
            .toList()
        if (nested.isEmpty()) return current
        main = firstMaxBy(nested) { it.area }
    }
}

/**
 * Direct children of the main container, in reading order. A ScrollView always has exactly one
 * child holding the real rows, so that one is unwrapped. Other single-child containers are NOT
 * unwrapped: a pager page is one item, and its children are parts of that item.
 */
fun contentRows(main: Node, p: Params): List<Node> {
    var rows: List<Node> = main.children
    if (p.unwrapScroll && main.type == "SCROLL" && rows.size == 1) {
        rows = rows[0].children
    }
    return sortedReading(rows)
}

/** `re.search(r"(?<!\w)" + re.escape(s) + r"(?!\w)", h)` for any haystack, with code-point boundaries. */
internal fun contained(s: String, haystacks: List<String>, p: Params): Boolean {
    if (s.codePointCount(0, s.length) < p.minDedupeLen) return false
    return haystacks.any { h ->
        var i = h.indexOf(s)
        var found = false
        while (i >= 0 && !found) {
            val end = i + s.length
            val freeBefore = i == 0 || !isPythonWordChar(h.codePointBefore(i))
            val freeAfter = end == h.length || !isPythonWordChar(h.codePointAt(end))
            found = freeBefore && freeAfter
            i = h.indexOf(s, i + 1)
        }
        found
    }
}

/**
 * Cleaned (text, desc). desc equal to text is dropped. With substring dedupe, visible text is only
 * dropped when an ancestor's *visible text* contains it (never in favour of an a11y summary); desc is
 * dropped when any ancestor label contains it.
 */
fun nodeLabel(n: Node, ancText: List<String>, ancAny: List<String>, p: Params): Pair<String?, String?> {
    var text = clean(n.text)
    var desc = clean(n.desc)
    if (desc == text) desc = null
    if (p.substringDedupe) {
        if (text != null && contained(text, ancText, p)) text = null
        if (desc != null && contained(desc, ancAny, p)) desc = null
    }
    return text to desc
}

/**
 * Flatten a subtree into entries: nodes without text/desc vanish (which also collapses single-child
 * chains); siblings are visited in reading order. [skip] holds ids of subtrees to exclude.
 */
fun collectEntries(
    node: Node,
    p: Params,
    out: MutableList<Entry>,
    skip: Set<Int> = emptySet(),
    ancText: List<String> = emptyList(),
    ancAny: List<String> = emptyList()
) {
    if (node.id in skip) return
    val (text, desc) = nodeLabel(node, ancText, ancAny, p)
    if (text != null || desc != null) {
        out.add(Entry(text, desc, node.x, node.y, node.w, node.h))
    }
    val rawText = clean(node.text)
    val rawDesc = clean(node.desc)
    val childAncText = if (rawText != null) ancText + rawText else ancText
    val childAncAny = ancAny + listOfNotNull(rawText, rawDesc)
    for (c in sortedReading(node.children)) {
        collectEntries(c, p, out, skip, childAncText, childAncAny)
    }
}

fun hasLabel(node: Node): Boolean = iterNodes(node).any { clean(it.text) != null || clean(it.desc) != null }
