package com.bolke.keyboard.samjho

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * A piece of readable text on screen and where it sits, in screen coordinates.
 *
 * Plain ints rather than a Rect: android.graphics.Rect throws in JVM unit tests, and
 * keeping the geometry primitive is what makes the hit-test checkable without a device.
 */
data class TextTarget(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val text: String
) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
    fun area(): Long = width().toLong() * height().toLong()
}

/**
 * Reads the text on screen out of the accessibility tree.
 *
 * The tap is caught by our own overlay rather than by a click event, because
 * TYPE_VIEW_CLICKED never fires for views without a click listener and WhatsApp
 * message bubbles are plain TextViews. So we record where every piece of text is,
 * then match her raw touch coordinates against those rectangles ourselves.
 */
object ScreenText {

    /** Guards against a pathological tree locking up the walk. */
    private const val NODE_BUDGET = 2000

    private val GURMUKHI = 0x0A00..0x0A7F

    fun capture(root: AccessibilityNodeInfo?, selfPackage: String): List<TextTarget> {
        if (root == null) return emptyList()

        val targets = ArrayList<TextTarget>()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        val bounds = Rect()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < NODE_BUDGET) {
            val node = queue.poll() ?: continue
            visited++

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }

            if (node.packageName == selfPackage) continue
            if (!node.isVisibleToUser) continue

            // WhatsApp puts the message body in contentDescription on some row layouts.
            val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
            if (!isTranslatable(text)) continue

            node.getBoundsInScreen(bounds)
            if (bounds.width() <= 0 || bounds.height() <= 0) continue

            targets.add(
                TextTarget(bounds.left, bounds.top, bounds.right, bounds.bottom, text)
            )
        }

        // ponytail: no recycle() — it is a no-op and deprecated from API 33, and this is a
        // one-shot walk per tap. Revisit only if node churn shows up in a profile.
        return targets
    }

    /**
     * The smallest rectangle containing the point, which is the most specific node
     * under her finger — a message bubble rather than the whole conversation list.
     */
    fun hitTest(targets: List<TextTarget>, x: Int, y: Int): TextTarget? =
        targets.filter { it.contains(x, y) }.minByOrNull { it.area() }

    /** Worth sending to a translator: has Latin letters and is not already Punjabi. */
    fun isTranslatable(text: String): Boolean {
        if (text.isBlank()) return false
        var latin = 0
        var gurmukhi = 0
        for (ch in text) {
            when {
                ch.code in GURMUKHI -> gurmukhi++
                ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
            }
        }
        return latin > 0 && latin > gurmukhi
    }
}
