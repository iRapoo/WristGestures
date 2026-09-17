package xyz.quenix.wristgestures.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import xyz.quenix.wristgestures.BuildConfig
import xyz.quenix.wristgestures.settings.GestureAction

/**
 * Executes a [GestureAction] through the accessibility APIs.
 *
 * Wear OS 3 has no public API to open the notification shade (there is no `statusbar`
 * service, so `GLOBAL_ACTION_NOTIFICATIONS` does nothing). Instead, the service does what
 * a finger would do:
 *
 * - **Scrolling** – finds the main vertical list on screen and asks it to scroll with
 *   `ACTION_SCROLL_FORWARD/BACKWARD`. This scrolls exactly one "page" and does not depend
 *   on screen size. If no list accepts the action, a swipe is emulated instead.
 * - **Opening notifications** – emulates a swipe up from the bottom of the watch face.
 * - **Back / Home** – regular global actions.
 */
class ActionPerformer(private val service: AccessibilityService) {

    /** Returns `true` if something was done. */
    fun perform(action: GestureAction): Boolean {
        if (BuildConfig.DEBUG) service.rootInActiveWindow?.let { dumpTree(it) }

        return when (action) {
            GestureAction.NONE -> false

            // On the watch face "next" opens notifications, exactly like flicking out did on
            // Wear OS 2. Some system UIs expose the watch face as a vertical pager (scrolling
            // it forward opens notifications), others do not (then a swipe up is emulated).
            GestureAction.NEXT -> scroll(forward = true) || swipe(SWIPE_OPEN_FROM, SWIPE_OPEN_TO)

            // Scroll up; at the top of a list scrolling back fails, so leave the screen instead.
            // On the watch face this scrolls the system pager back, which opens quick settings
            // (and "next" inside quick settings closes them again).
            GestureAction.PREVIOUS -> scroll(forward = false) || back()

            GestureAction.OPEN_NOTIFICATIONS -> swipe(SWIPE_OPEN_FROM, SWIPE_OPEN_TO)
            GestureAction.BACK -> back()
            GestureAction.HOME -> globalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Scrolling
    // ---------------------------------------------------------------------------------------

    private fun scroll(forward: Boolean): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val list = findVerticalScrollable(root, forward) ?: return false

        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val done = list.performAction(action)
        log("scroll forward=$forward on ${list.className} -> $done")
        return done
    }

    /**
     * Finds the biggest on-screen node that can scroll vertically in the requested direction.
     *
     * A node only reports scroll actions that are possible right now: a list already at the
     * top has no `ACTION_SCROLL_BACKWARD`. That is what lets [GestureAction.PREVIOUS] fall
     * back to "back" at the top of the notification list.
     *
     * Horizontal containers (tile pagers, carousels) are skipped, otherwise flicking on the
     * watch face would switch tiles instead of opening notifications.
     *
     * Among nodes of equal size the **deepest** one wins. System UI often nests a full-screen
     * list inside a full-screen panel: the list must scroll first, and only when it cannot
     * scroll any further does the panel get the action (e.g. the notification panel closes
     * when its list is already at the top).
     */
    private fun findVerticalScrollable(root: AccessibilityNodeInfo, forward: Boolean): AccessibilityNodeInfo? {
        val wanted = if (forward) AccessibilityAction.ACTION_SCROLL_FORWARD else AccessibilityAction.ACTION_SCROLL_BACKWARD
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val bounds = Rect()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)

            if (!node.isVisibleToUser || !node.isScrollable) continue
            if (wanted !in node.actionList || isHorizontal(node)) continue

            node.getBoundsInScreen(bounds)
            val area = bounds.width() * bounds.height()
            // BFS visits parents before children, so ">=" lets a nested node replace its parent.
            if (area >= bestArea) {
                best = node
                bestArea = area
            }
        }
        return best
    }

    private fun isHorizontal(node: AccessibilityNodeInfo): Boolean {
        val actions = node.actionList
        // Views that explicitly declare a direction.
        if (AccessibilityAction.ACTION_SCROLL_LEFT in actions || AccessibilityAction.ACTION_SCROLL_RIGHT in actions) {
            if (AccessibilityAction.ACTION_SCROLL_UP !in actions && AccessibilityAction.ACTION_SCROLL_DOWN !in actions) return true
        }
        if (AccessibilityAction.ACTION_PAGE_LEFT in actions || AccessibilityAction.ACTION_PAGE_RIGHT in actions) return true

        // A single-row collection is a horizontal list.
        node.collectionInfo?.let { if (it.rowCount == 1 && it.columnCount > 1) return true }

        val className = node.className?.toString().orEmpty()
        return HORIZONTAL_CLASS_HINTS.any { className.contains(it) }
    }

    // ---------------------------------------------------------------------------------------
    // Swipes and global actions
    // ---------------------------------------------------------------------------------------

    /**
     * Emulates a vertical swipe through the screen center.
     * [fromY] and [toY] are fractions of the screen height (0 = top, 1 = bottom).
     */
    private fun swipe(fromY: Float, toY: Float): Boolean {
        val metrics = service.resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val path = Path().apply {
            moveTo(x, metrics.heightPixels * fromY)
            lineTo(x, metrics.heightPixels * toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
            .build()
        val accepted = service.dispatchGesture(gesture, null, null)
        log("swipe $fromY -> $toY accepted=$accepted")
        return accepted
    }

    /**
     * "Back", except on the watch face. There is nothing to go back to from the watch face, and
     * some system UIs (Mobvoi TicWatch) treat Back there like the side button and toggle the
     * app list.
     */
    private fun back(): Boolean =
        !isWatchFace() && globalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    /**
     * Detects the watch face screen.
     *
     * The watch face itself is drawn on a surface, so it has no accessibility nodes with text and
     * nothing clickable, while every other screen (notifications, app list, quick settings,
     * tiles, apps, the charging screen) has at least one. Content descriptions are ignored: the
     * watch face layout carries one for screen readers (for example "Watch face 13:07").
     */
    private fun isWatchFace(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.isVisibleToUser) continue
            if (node.isClickable || !node.text.isNullOrEmpty()) return false
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        log("watch face detected, back skipped")
        return true
    }

    private fun globalAction(action: Int): Boolean =
        service.performGlobalAction(action).also { log("global action $action -> $it") }

    // ---------------------------------------------------------------------------------------
    // Debugging
    // ---------------------------------------------------------------------------------------

    /**
     * Debug builds print the current window structure to logcat before every action:
     * `adb logcat -s WristGestures`. Useful when a watch's system UI behaves differently.
     */
    private fun dumpTree(node: AccessibilityNodeInfo, depth: Int = 0) {
        val bounds = Rect().also(node::getBoundsInScreen)
        val flags = buildString {
            if (node.isScrollable) append(" scrollable")
            if (node.isClickable) append(" clickable")
            if (!node.text.isNullOrEmpty()) append(" text")
            if (!node.contentDescription.isNullOrEmpty()) append(" desc=\"${node.contentDescription.take(40)}\"")
            if (!node.isVisibleToUser) append(" hidden")
        }
        val actions = node.actionList.joinToString(",") { it.id.toString(16) }
        log("${"  ".repeat(depth)}${node.className} id=${node.viewIdResourceName} $bounds$flags actions=[$actions]")
        for (i in 0 until node.childCount) node.getChild(i)?.let { dumpTree(it, depth + 1) }
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "WristGestures"

        /** Swipe from 85% to 15% of the height: long enough to be recognized as a swipe up. */
        const val SWIPE_OPEN_FROM = 0.85f
        const val SWIPE_OPEN_TO = 0.15f
        const val SWIPE_DURATION_MS = 200L

        val HORIZONTAL_CLASS_HINTS = listOf("ViewPager", "HorizontalScrollView", "HorizontalPager")
    }
}
