package de.mimuc.senseeverything.contentextraction

/*
 * Port of the content extraction harness (ba-auswertung: scripts/python/content_extraction), frozen
 * configuration D / rows / v1 (DEPLOYMENT.md there). The prompt must be byte-identical to the
 * harness's, otherwise device results can't be compared with the harness runs, so this code follows
 * the Python line by line. ContentExtractionParityTest checks it against prompts exported from the
 * harness.
 *
 * Only the code path of the frozen configuration is ported: anchor segmentation with fallback
 * "whole", offset "separator", geometry "rows". Not ported: the "cycle"/"rows" modes, edge-bar
 * chrome (off in the frozen config) and the "rows+roles" geometry.
 */

/** One accessibility node. Bounds are screen-relative (0..1), parsed from the stored decimal text. */
class Node(
    val id: Int,
    /** CONTAINER, BUTTON, TEXT, IMAGE, LIST, SCROLL, INPUT, UNKNOWN, ... */
    val type: String,
    val text: String?,
    /** contentDescription */
    val desc: String?,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val scrollable: Boolean = false,
    val children: MutableList<Node> = mutableListOf()
) {
    val bottom: Double get() = y + h
    val area: Double get() = w * h
}

/**
 * One labelled node after cleaning/dedupe (at least one of text/desc is set), with the bounds of
 * the node it came from so the serializer can put side-by-side entries on one line.
 */
class Entry(
    /** visible text */
    val text: String?,
    /** contentDescription */
    val desc: String?,
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double
) {
    val bottom: Double get() = y + h
    val right: Double get() = x + w
}

/** The harness's `Params()` defaults with `geometry="rows"`, limited to the fields the frozen path reads. */
data class Params(
    /** descend into a nested scrollable covering >= this share of the current one */
    val nestRatio: Double = 0.7,
    /** a SCROLL with one child: rows are that child's children */
    val unwrapScroll: Boolean = true,
    /** main container without any text, but text elsewhere: whole screen is content */
    val emptyMainFallback: Boolean = true,
    /** drop labels already contained in an ancestor's label */
    val substringDedupe: Boolean = true,
    /** shorter labels (in code points, e.g. counts like "3") are never substring-deduped */
    val minDedupeLen: Int = 3,
    /** tolerance for "touches the viewport edge" */
    val edgeEps: Double = 0.005,
    /** min overlap of node-type sets for two row signatures to match */
    val typeJaccard: Double = 0.75,
    val heightBuckets: List<Double> = listOf(0.02, 0.08, 0.25, 0.6),
    /** vertical overlap, as share of the smaller height, for two entries to share a line */
    val sameLineOverlap: Double = 0.5
)

enum class SegmentationMode(val harnessName: String) {
    /** a recurring row signature starts every segment */
    ANCHOR("anchor"),
    /** no anchor found: the whole content is one segment, without segment headers */
    WHOLE("whole")
}

class Segment(
    val entries: List<Entry>,
    val clippedTop: Boolean,
    val clippedBottom: Boolean,
    val rowIds: List<Int>
)

class Screen(
    /** mode actually used: anchor may have fallen back to whole */
    val mode: SegmentationMode,
    val chrome: List<Entry>,
    val segments: List<Segment>,
    /** node id of the main scrollable container (root id if none) */
    val mainId: Int,
    /** true when the empty-main fallback replaced the main container by the root */
    val mainFallback: Boolean,
    /** index of the anchor row; null without an anchor */
    val anchorIndex: Int?,
    /** signature of the anchor row (debug only) */
    val anchor: String?,
    /** debug only */
    val rowSignatures: List<String>
)
