package de.mimuc.senseeverything.service.accessibility

import android.graphics.Point
import android.graphics.Rect
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.accessibility.model.InteractionEvent
import de.mimuc.senseeverything.service.accessibility.model.InteractionType
import de.mimuc.senseeverything.service.accessibility.model.NodeType
import de.mimuc.senseeverything.service.accessibility.model.ScreenRegion
import de.mimuc.senseeverything.service.accessibility.model.ScreenSnapshot
import de.mimuc.senseeverything.service.accessibility.model.SizeClass
import de.mimuc.senseeverything.service.accessibility.model.SkeletonNode
import de.mimuc.senseeverything.service.accessibility.model.TextCategory
import de.mimuc.senseeverything.service.accessibility.model.TreeSkeleton
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Captures privacy-preserving UI tree skeletons and interactions.
 *
 * [consumeEvent] is called on the main thread of the `:remote` process, which is shared with the
 * LogService and its sensors. It therefore only does cheap bookkeeping and hands all tree traversal
 * (binder calls into the foreground app) to a dedicated worker thread. The worker is a single thread,
 * so captures and interactions are processed in the order the events arrived.
 */
class UITreeConsumer : AccessibilityLoggingConsumer {
    companion object {
        const val TAG = "UITreeConsumer"

        private val WHITESPACE = "\\s+".toRegex()
        private const val FRAMEWORK_SEARCH_DEPTH = 3
        private const val MAX_PENDING_INTERACTIONS = 200
        private val STATS_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5)
    }

    lateinit var service: AccessibilityLogService

    private val screenSize = Point()
    private lateinit var batchManager: SnapshotBatchManager

    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler

    // Main-thread state
    // Debouncing for WINDOW_CONTENT_CHANGED events
    private var lastContentChangeTime = 0L
    private val contentChangeDebounceMs = 500L
    private val scrollCoalescer = ScrollCoalescer()

    // Worker-thread state
    private var lastSignature: String? = null
    private var currentSkeleton: TreeSkeleton? = null
    private val nodeBounds = Rect()

    // Shared between main thread and worker
    private val capturePending = AtomicBoolean(false)
    // wall-clock time of the latest event that requested a capture
    private val captureTriggerTime = AtomicLong(0L)
    private val pendingInteractions = AtomicInteger(0)
    private val stats = CaptureStats()

    private val captureRunnable = Runnable {
        capturePending.set(false)
        val eventTime = captureTriggerTime.get()
        stats.recordCaptureDelay(System.currentTimeMillis() - eventTime)
        runSafely("capture") { captureTreeSkeleton(eventTime) }
    }

    private val statsRunnable = object : Runnable {
        override fun run() {
            logStats()
            worker.postDelayed(this, STATS_INTERVAL_MS)
        }
    }

    override fun init(service: AccessibilityLogService) {
        this.service = service

        // Get screen dimensions
        val windowManager = service.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getSize(screenSize)

        batchManager = SnapshotBatchManager(service.database)

        workerThread = HandlerThread("UITreeCapture", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        worker = Handler(workerThread.looper)
        worker.postDelayed(statsRunnable, STATS_INTERVAL_MS)

        WHALELog.i(TAG, "Initialized with screen size: ${screenSize.x}x${screenSize.y}")
    }

    override fun consumeEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                stats.stateEvents.incrementAndGet()
                scheduleCapture(event)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                stats.contentEvents.incrementAndGet()
                // Debounced capture for content changes
                val now = System.currentTimeMillis()
                if (now - lastContentChangeTime < contentChangeDebounceMs) {
                    // Too soon, skip this event
                    stats.contentDebounced.incrementAndGet()
                    return
                }
                lastContentChangeTime = now
                scheduleCapture(event)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                scheduleInteraction(event, InteractionType.TAP)
            }

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                scheduleInteraction(event, InteractionType.LONG_PRESS)
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // one SCROLL interaction per scroll gesture instead of one per scroll event
                if (scrollCoalescer.shouldRecord(event.packageName, event.className)) {
                    scheduleInteraction(event, InteractionType.SCROLL)
                } else {
                    stats.scrollsCoalesced.incrementAndGet()
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // whether the source is editable is checked on the worker
                scheduleInteraction(event, InteractionType.TEXT_INPUT)
            }

            // Gesture events for interactions like double-tap
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                scheduleInteraction(event, InteractionType.TAP)
            }

            // Touch exploration for accessibility features
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                // Could track touch start positions if needed
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                scheduleInteraction(event, InteractionType.TAP)
            }
        }
    }

    /**
     * Converts the event's creation time (uptime in the emitting app) to wall-clock time, so snapshots
     * are stamped with when the event happened rather than when it was processed.
     */
    private fun eventWallTime(event: AccessibilityEvent): Long =
        System.currentTimeMillis() - (SystemClock.uptimeMillis() - event.eventTime)

    /**
     * Queues a capture unless one is already waiting. A waiting capture reads the live tree when it
     * runs, so a second one would see the same screen and be dropped by the signature check anyway.
     * The capture is stamped with the latest triggering event, since that is the state it reads.
     */
    private fun scheduleCapture(event: AccessibilityEvent) {
        captureTriggerTime.set(eventWallTime(event))
        if (capturePending.compareAndSet(false, true)) {
            worker.post(captureRunnable)
        } else {
            stats.capturesCoalesced.incrementAndGet()
        }
    }

    private fun scheduleInteraction(event: AccessibilityEvent, type: InteractionType) {
        if (pendingInteractions.incrementAndGet() > MAX_PENDING_INTERACTIONS) {
            // the worker is far behind, don't let the queue grow without bound
            pendingInteractions.decrementAndGet()
            stats.interactionsDropped.incrementAndGet()
            return
        }

        // the framework may reuse the event after onAccessibilityEvent returns, so keep a copy
        val copy = AccessibilityEvent(event)
        val eventTime = eventWallTime(event)
        worker.post {
            try {
                runSafely("interaction") { handleInteraction(copy, type, eventTime) }
            } finally {
                pendingInteractions.decrementAndGet()
            }
        }
    }

    private inline fun runSafely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // an uncaught exception on the worker would take down the whole :remote process
            WHALELog.e(TAG, "Failed to process $what: ${e.message}", e)
        }
    }

    private fun captureTreeSkeleton(eventTime: Long) {
        val start = SystemClock.elapsedRealtime()
        val rootNode = service.rootInActiveWindow ?: return

        try {
            val framework = detectFramework(rootNode)
            val nodes = mutableListOf<SkeletonNode>()

            // Build flattened skeleton tree
            buildSkeleton(rootNode, null, 0, nodes)
            stats.capturesRun.incrementAndGet()
            stats.nodesVisited.addAndGet(nodes.size.toLong())

            // Skip if tree is empty (all nodes were invisible)
            if (nodes.isEmpty()) {
                WHALELog.i(TAG, "Skipping empty tree (all nodes invisible)")
                return
            }

            // Generate signature for deduplication
            val signature = TreeSignature.compute(nodes)
            val skeleton = TreeSkeleton(signature = signature, nodes = nodes)

            // Only create snapshot if screen structure changed
            if (signature != lastSignature) {
                val snapshot = ScreenSnapshot(
                    timestamp = eventTime,
                    appPackage = rootNode.packageName?.toString() ?: "unknown",
                    framework = framework,
                    skeleton = skeleton,
                    interaction = null
                )

                currentSkeleton = skeleton
                lastSignature = signature

                processSnapshot(snapshot)
                stats.screensRecorded.incrementAndGet()

                WHALELog.d(TAG, "New screen captured: ${snapshot.appPackage}, signature: ${signature.take(8)}..., nodes: ${nodes.size}")
            }
        } finally {
            rootNode.recycle()
            stats.recordCaptureTime(SystemClock.elapsedRealtime() - start)
        }
    }

    private fun buildSkeleton(
        node: AccessibilityNodeInfo,
        parentId: Int?,
        depth: Int,
        nodes: MutableList<SkeletonNode>
    ) {
        val nodeId = nodes.size

        // only used before recursing, so a single instance can be reused on the worker thread
        val bounds = nodeBounds
        node.getBoundsInScreen(bounds)

        // Skip invisible or out-of-bounds nodes
        if (!node.isVisibleToUser || bounds.width() == 0 || bounds.height() == 0) {
            return
        }

        val className = node.className?.toString()?.lowercase()

        val skeletonNode = SkeletonNode(
            id = nodeId,
            parentId = parentId,
            type = classifyNodeType(node, className),
            depth = depth,
            region = calculateRegion(bounds),
            sizeClass = calculateSizeClass(bounds),
            relativeX = bounds.left.toFloat() / screenSize.x,
            relativeY = bounds.top.toFloat() / screenSize.y,
            relativeWidth = bounds.width().toFloat() / screenSize.x,
            relativeHeight = bounds.height().toFloat() / screenSize.y,
            clickable = node.isClickable,
            scrollable = node.isScrollable,
            editable = node.isEditable,
            focusable = node.isFocusable,
            hasText = node.text != null || node.contentDescription != null,
            textCategory = categorizeText(node),
            hasImage = isImageNode(className),
            role = extractRole(node)
        )

        nodes.add(skeletonNode)

        // Recurse to children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                buildSkeleton(child, nodeId, depth + 1, nodes)
                child.recycle()
            }
        }
    }

    /** @param className the node's class name, already lowercased */
    private fun classifyNodeType(node: AccessibilityNodeInfo, className: String?): NodeType {
        if (className == null) return NodeType.UNKNOWN

        return when {
            // List containers
            className.contains("recyclerview") ||
            className.contains("listview") ||
            className.contains("flatlist") -> NodeType.LIST

            // Scroll containers
            className.contains("scrollview") ||
            className.contains("nestedscrollview") -> NodeType.SCROLL

            // Input fields
            className.contains("edittext") ||
            className.contains("textfield") ||
            className.contains("textinput") -> NodeType.INPUT

            // Buttons
            className.contains("button") -> NodeType.BUTTON

            // Clickable text (common in cross-platform frameworks)
            node.isClickable && className.contains("text") -> NodeType.BUTTON

            // Images
            className.contains("image") -> NodeType.IMAGE

            // Video
            className.contains("video") -> NodeType.VIDEO

            // WebView
            className.contains("webview") -> NodeType.WEB

            // Text
            className.contains("text") -> NodeType.TEXT

            // Container (has children)
            node.childCount > 0 -> NodeType.CONTAINER

            else -> NodeType.UNKNOWN
        }
    }

    private fun categorizeText(node: AccessibilityNodeInfo): TextCategory? {
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: return null

        val wordCount = text.trim().split(WHITESPACE).size

        return when {
            wordCount == 0 || text.isBlank() -> TextCategory.EMPTY
            wordCount <= 2 -> TextCategory.SINGLE_WORD
            wordCount <= 10 -> TextCategory.SHORT_PHRASE
            wordCount <= 30 -> TextCategory.SENTENCE
            wordCount <= 100 -> TextCategory.PARAGRAPH
            else -> TextCategory.LONG_TEXT
        }
    }

    /** @param className the node's class name, already lowercased */
    private fun isImageNode(className: String?): Boolean {
        return className?.contains("image") ?: false
    }

    private fun extractRole(node: AccessibilityNodeInfo): String? {
        // Try to extract semantic role from accessibility metadata
        node.extras?.let { bundle ->
            // React Native accessibility role
            bundle.getString("accessibilityRole")?.let {
                return it
            }

            // Android role description (API 28+)
            bundle.getCharSequence("AccessibilityNodeInfo.roleDescription")?.let {
                return it.toString()
            }
        }

        return null
    }

    private fun calculateRegion(bounds: Rect): ScreenRegion {
        val centerX = bounds.centerX().toFloat() / screenSize.x
        val centerY = bounds.centerY().toFloat() / screenSize.y

        val col = when {
            centerX < 0.33f -> 0
            centerX < 0.67f -> 1
            else -> 2
        }

        val row = when {
            centerY < 0.33f -> 0
            centerY < 0.67f -> 1
            else -> 2
        }

        return ScreenRegion.values()[row * 3 + col]
    }

    private fun calculateSizeClass(bounds: Rect): SizeClass {
        val area = bounds.width() * bounds.height()
        val screenArea = screenSize.x * screenSize.y
        val percentage = area.toFloat() / screenArea

        return when {
            percentage < 0.05f -> SizeClass.TINY
            percentage < 0.15f -> SizeClass.SMALL
            percentage < 0.40f -> SizeClass.MEDIUM
            percentage < 0.80f -> SizeClass.LARGE
            else -> SizeClass.FULLSCREEN
        }
    }

    /**
     * Walks the top [FRAMEWORK_SEARCH_DEPTH] levels once (including invisible nodes) and matches
     * the collected class names in priority order. Same result as searching once per marker.
     */
    private fun detectFramework(rootNode: AccessibilityNodeInfo): String {
        val classNames = ArrayList<String>()
        collectClassNames(rootNode, FRAMEWORK_SEARCH_DEPTH, classNames)

        fun found(marker: String) = classNames.any { it.contains(marker) }

        return when {
            found("com.facebook.react.ReactRootView") -> "REACT_NATIVE"
            found("io.flutter.embedding.android.FlutterView") ||
                found("io.flutter.view.FlutterView") -> "FLUTTER"
            found("android.webkit.WebView") -> "WEBVIEW"
            found("com.unity3d.player.UnityPlayer") -> "UNITY"
            found("md5") || found("mono.android") -> "XAMARIN"
            else -> "NATIVE"
        }
    }

    private fun collectClassNames(node: AccessibilityNodeInfo, maxDepth: Int, out: MutableList<String>) {
        if (maxDepth <= 0) return

        node.className?.let { out.add(it.toString()) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectClassNames(child, maxDepth - 1, out)
                child.recycle()
            }
        }
    }

    private fun handleInteraction(event: AccessibilityEvent, type: InteractionType, eventTime: Long) {
        val source = event.source ?: return

        if (type == InteractionType.TEXT_INPUT && !source.isEditable) {
            source.recycle()
            return
        }

        recordInteraction(source, type, eventTime)
    }

    private fun recordInteraction(source: AccessibilityNodeInfo, type: InteractionType, eventTime: Long) {
        val currentSkel = currentSkeleton ?: run {
            source.recycle()
            return
        }

        try {
            val bounds = Rect()
            source.getBoundsInScreen(bounds)

            // Find matching node in current skeleton by spatial matching
            val nodeId = findNodeIdByBounds(bounds, currentSkel)

            if (nodeId != null) {
                // while positioning on keyboard is not precise anyway, don't return any position anyway for text entry
                val interaction =
                    if (type == InteractionType.TEXT_INPUT) InteractionEvent.unpositioned(
                        type = type,
                        targetNodeId = nodeId
                    ) else InteractionEvent(
                        type = type,
                        targetNodeId = nodeId,
                        tapX = bounds.centerX().toFloat() / screenSize.x,
                        tapY = bounds.centerY().toFloat() / screenSize.y
                    )

                val snapshot = ScreenSnapshot(
                    timestamp = eventTime,
                    appPackage = source.packageName?.toString() ?: "unknown",
                    framework = "", // Not needed for interaction-only events
                    skeleton = TreeSkeleton(lastSignature ?: "", emptyList()), // Reference only
                    interaction =  interaction
                )

                processSnapshot(snapshot)
                stats.interactionsRecorded.incrementAndGet()

                WHALELog.d(TAG, "Interaction recorded: ${type.name} on node $nodeId at (${interaction.tapX}, ${interaction.tapY})")
            }
        } finally {
            source.recycle()
        }
    }

    private fun findNodeIdByBounds(bounds: Rect, skeleton: TreeSkeleton): Int? {
        // Find the smallest node that contains the bounds center
        val centerX = bounds.centerX().toFloat() / screenSize.x
        val centerY = bounds.centerY().toFloat() / screenSize.y

        var bestMatch: SkeletonNode? = null
        var smallestArea = Float.MAX_VALUE

        for (node in skeleton.nodes) {
            // Check if center point is within node bounds
            if (centerX >= node.relativeX &&
                centerX <= node.relativeX + node.relativeWidth &&
                centerY >= node.relativeY &&
                centerY <= node.relativeY + node.relativeHeight) {

                val area = node.relativeWidth * node.relativeHeight
                if (area < smallestArea) {
                    smallestArea = area
                    bestMatch = node
                }
            }
        }

        return bestMatch?.id
    }

    private fun processSnapshot(snapshot: ScreenSnapshot) {
        batchManager.addSnapshot(snapshot)
    }

    /** Logs pipeline load for the last interval so outages can be related to capture work. */
    private fun logStats() {
        val runtime = Runtime.getRuntime()
        val heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val heapMaxMb = runtime.maxMemory() / (1024 * 1024)
        val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

        WHALELog.i(TAG, "stats: " + stats.drain() +
                " pendingInteractions=${pendingInteractions.get()}" +
                " heapUsedMb=$heapUsedMb heapMaxMb=$heapMaxMb nativeHeapMb=$nativeHeapMb")
    }

    override fun shutdown() {
        worker.removeCallbacksAndMessages(null)
        workerThread.quitSafely()
        batchManager.shutdown()
    }

    /** Counters for the periodic stats log, reset on every report. */
    private class CaptureStats {
        val stateEvents = AtomicLong()
        val contentEvents = AtomicLong()
        val contentDebounced = AtomicLong()
        val capturesCoalesced = AtomicLong()
        val capturesRun = AtomicLong()
        val screensRecorded = AtomicLong()
        val nodesVisited = AtomicLong()
        val captureMsTotal = AtomicLong()
        val captureMsMax = AtomicLong()
        val captureDelayMsMax = AtomicLong()
        val interactionsRecorded = AtomicLong()
        val scrollsCoalesced = AtomicLong()
        val interactionsDropped = AtomicLong()

        fun recordCaptureTime(ms: Long) {
            captureMsTotal.addAndGet(ms)
            captureMsMax.accumulateAndGet(ms) { a, b -> maxOf(a, b) }
        }

        /** Time from the triggering event to the start of the capture on the worker. */
        fun recordCaptureDelay(ms: Long) {
            captureDelayMsMax.accumulateAndGet(ms) { a, b -> maxOf(a, b) }
        }

        fun drain(): String =
            "stateEvents=${stateEvents.getAndSet(0)}" +
                " contentEvents=${contentEvents.getAndSet(0)}" +
                " contentDebounced=${contentDebounced.getAndSet(0)}" +
                " capturesCoalesced=${capturesCoalesced.getAndSet(0)}" +
                " capturesRun=${capturesRun.getAndSet(0)}" +
                " screensRecorded=${screensRecorded.getAndSet(0)}" +
                " nodesVisited=${nodesVisited.getAndSet(0)}" +
                " captureMsTotal=${captureMsTotal.getAndSet(0)}" +
                " captureMsMax=${captureMsMax.getAndSet(0)}" +
                " captureDelayMsMax=${captureDelayMsMax.getAndSet(0)}" +
                " interactionsRecorded=${interactionsRecorded.getAndSet(0)}" +
                " scrollsCoalesced=${scrollsCoalesced.getAndSet(0)}" +
                " interactionsDropped=${interactionsDropped.getAndSet(0)}"
    }
}
