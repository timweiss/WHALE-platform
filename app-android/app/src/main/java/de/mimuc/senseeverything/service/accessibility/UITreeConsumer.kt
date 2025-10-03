package de.mimuc.senseeverything.service.accessibility

import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import de.mimuc.senseeverything.service.accessibility.model.InteractionEvent
import de.mimuc.senseeverything.service.accessibility.model.InteractionType
import de.mimuc.senseeverything.service.accessibility.model.NodeType
import de.mimuc.senseeverything.service.accessibility.model.ScreenRegion
import de.mimuc.senseeverything.service.accessibility.model.ScreenSnapshot
import de.mimuc.senseeverything.service.accessibility.model.SizeClass
import de.mimuc.senseeverything.service.accessibility.model.SkeletonNode
import de.mimuc.senseeverything.service.accessibility.model.TextCategory
import de.mimuc.senseeverything.service.accessibility.model.TreeSkeleton
import java.security.MessageDigest

class UITreeConsumer : AccessibilityLoggingConsumer {
    companion object {
        const val TAG = "UITreeConsumer"
    }

    lateinit var service: AccessibilityLogService

    private var lastSignature: String? = null
    private var currentSkeleton: TreeSkeleton? = null
    private val screenSize = Point()
    private lateinit var batchManager: SnapshotBatchManager

    // Debouncing for WINDOW_CONTENT_CHANGED events
    private var lastContentChangeTime = 0L
    private val contentChangeDebounceMs = 500L

    override fun init(service: AccessibilityLogService) {
        this.service = service

        // Get screen dimensions
        val windowManager = service.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getSize(screenSize)

        batchManager = SnapshotBatchManager(service)

        Log.d(TAG, "Initialized with screen size: ${screenSize.x}x${screenSize.y}")
    }

    override fun consumeEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                captureTreeSkeleton(event)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Debounced capture for content changes
                captureTreeSkeletonDebounced(event)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                recordInteraction(event, InteractionType.TAP)
            }

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                recordInteraction(event, InteractionType.LONG_PRESS)
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                recordInteraction(event, InteractionType.SCROLL)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val source = event.source
                if (source?.isEditable == true) {
                    recordInteraction(event, InteractionType.TEXT_INPUT)
                }
                source?.recycle()
            }

            // Gesture events for interactions like double-tap
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                recordInteraction(event, InteractionType.TAP)
            }

            // Touch exploration for accessibility features
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                // Could track touch start positions if needed
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                recordInteraction(event, InteractionType.TAP)
            }
        }
    }

    private fun captureTreeSkeleton(event: AccessibilityEvent) {
        val rootNode = service.rootInActiveWindow ?: return

        try {
            val framework = detectFramework(rootNode)
            val nodes = mutableListOf<SkeletonNode>()

            // Build flattened skeleton tree
            buildSkeleton(rootNode, null, 0, nodes)

            // Skip if tree is empty (all nodes were invisible)
            if (nodes.isEmpty()) {
                Log.d(TAG, "Skipping empty tree (all nodes invisible)")
                return
            }

            val skeleton = TreeSkeleton(
                signature = "", // Will be computed next
                nodes = nodes
            )

            // Generate signature for deduplication
            val signature = generateTreeSignature(skeleton)
            skeleton.signature = signature

            // Only create snapshot if screen structure changed
            if (signature != lastSignature) {
                val snapshot = ScreenSnapshot(
                    timestamp = System.currentTimeMillis(),
                    appPackage = rootNode.packageName?.toString() ?: "unknown",
                    framework = framework,
                    skeleton = skeleton,
                    interaction = null
                )

                currentSkeleton = skeleton
                lastSignature = signature

                processSnapshot(snapshot)

                Log.d(TAG, "New screen captured: ${snapshot.appPackage}, signature: ${signature.take(8)}..., nodes: ${nodes.size}")
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun captureTreeSkeletonDebounced(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        if (now - lastContentChangeTime < contentChangeDebounceMs) {
            // Too soon, skip this event
            return
        }
        lastContentChangeTime = now
        captureTreeSkeleton(event)
    }

    private fun buildSkeleton(
        node: AccessibilityNodeInfo,
        parentId: Int?,
        depth: Int,
        nodes: MutableList<SkeletonNode>
    ) {
        val nodeId = nodes.size

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Skip invisible or out-of-bounds nodes
        if (!node.isVisibleToUser || bounds.width() == 0 || bounds.height() == 0) {
            return
        }

        val skeletonNode = SkeletonNode(
            id = nodeId,
            parentId = parentId,
            type = classifyNodeType(node),
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
            hasImage = isImageNode(node),
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

    private fun classifyNodeType(node: AccessibilityNodeInfo): NodeType {
        val className = node.className?.toString()?.lowercase() ?: return NodeType.UNKNOWN

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

        val wordCount = text.trim().split("\\s+".toRegex()).size

        return when {
            wordCount == 0 || text.isBlank() -> TextCategory.EMPTY
            wordCount <= 2 -> TextCategory.SINGLE_WORD
            wordCount <= 10 -> TextCategory.SHORT_PHRASE
            wordCount <= 30 -> TextCategory.SENTENCE
            wordCount <= 100 -> TextCategory.PARAGRAPH
            else -> TextCategory.LONG_TEXT
        }
    }

    private fun isImageNode(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString()?.lowercase() ?: return false
        return className.contains("image")
    }

    private fun extractRole(node: AccessibilityNodeInfo): String? {
        // Try to extract semantic role from accessibility metadata
        node.extras?.let { bundle ->
            // React Native accessibility role
            bundle.getString("accessibilityRole")?.let {
                return it
            }

            // Android role description (API 28+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                bundle.getCharSequence("AccessibilityNodeInfo.roleDescription")?.let {
                    return it.toString()
                }
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

    private fun detectFramework(rootNode: AccessibilityNodeInfo): String {
        if (searchForClassName(rootNode, "com.facebook.react.ReactRootView")) {
            return "REACT_NATIVE"
        }
        if (searchForClassName(rootNode, "io.flutter.embedding.android.FlutterView") ||
            searchForClassName(rootNode, "io.flutter.view.FlutterView")) {
            return "FLUTTER"
        }
        if (searchForClassName(rootNode, "android.webkit.WebView")) {
            return "WEBVIEW"
        }
        if (searchForClassName(rootNode, "com.unity3d.player.UnityPlayer")) {
            return "UNITY"
        }
        if (searchForClassName(rootNode, "md5") ||
            searchForClassName(rootNode, "mono.android")) {
            return "XAMARIN"
        }
        return "NATIVE"
    }

    private fun searchForClassName(node: AccessibilityNodeInfo, className: String, maxDepth: Int = 3): Boolean {
        if (maxDepth <= 0) return false

        if (node.className?.toString()?.contains(className) == true) {
            return true
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = searchForClassName(child, className, maxDepth - 1)
                child.recycle()
                if (found) return true
            }
        }
        return false
    }

    private fun generateTreeSignature(skeleton: TreeSkeleton): String {
        val structureString = skeleton.nodes.joinToString("|") { node ->
            "${node.type.name}:${node.depth}:${node.region.name}:${node.sizeClass.name}:${node.clickable}"
        }

        return hashString(structureString)
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun recordInteraction(event: AccessibilityEvent, type: InteractionType) {
        val source = event.source ?: return
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
                val interaction = InteractionEvent(
                    type = type,
                    targetNodeId = nodeId,
                    tapX = bounds.centerX().toFloat() / screenSize.x,
                    tapY = bounds.centerY().toFloat() / screenSize.y
                )

                val snapshot = ScreenSnapshot(
                    timestamp = System.currentTimeMillis(),
                    appPackage = source.packageName?.toString() ?: "unknown",
                    framework = "", // Not needed for interaction-only events
                    skeleton = TreeSkeleton(lastSignature ?: "", emptyList()), // Reference only
                    interaction = interaction
                )

                processSnapshot(snapshot)

                Log.d(TAG, "Interaction recorded: ${type.name} on node $nodeId at (${interaction.tapX}, ${interaction.tapY})")
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

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val stats = batchManager.getStats()
            Log.d(TAG, "Snapshot added. Queue: ${stats.queueSize}")
        }
    }

    override fun shutdown() {
        batchManager.shutdown()
    }
}
