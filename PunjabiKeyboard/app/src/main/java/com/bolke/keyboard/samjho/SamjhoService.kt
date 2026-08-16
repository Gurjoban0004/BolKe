package com.bolke.keyboard.samjho

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import com.bolke.keyboard.R
import com.bolke.keyboard.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Samjho: reads the text on screen so an English message can be shown in Punjabi.
 *
 * Phase 1 is the bubble only. It parks on a screen edge, drags, and remembers where
 * it was left. Tapping it is wired up in phase 2.
 */
class SamjhoService : AccessibilityService() {

    /** Only AIMING and SHOWING care about screen events. IDLE must stay cheap. */
    private enum class State { IDLE, AIMING, TRANSLATING, SHOWING }

    private lateinit var prefs: PreferencesManager
    private lateinit var translator: PunjabiTranslator
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var catcher: View? = null
    private var card: View? = null
    private var cardParams: WindowManager.LayoutParams? = null
    private var state = State.IDLE
    private var targets: List<TextTarget> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private val exitAim = Runnable { endAiming() }
    private val dismissCard = Runnable { removeCard() }
    private val resnapshot = Runnable { targets = snapshot() }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PreferencesManager(this)
        translator = PunjabiTranslator(prefs)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        instance = this
        if (prefs.isSamjhoBubbleEnabled) showBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // typeWindowContentChanged fires constantly. Doing any work here while idle
        // is the difference between a normal battery day and a dead phone by noon.
        if (state == State.IDLE) return
        if (event == null || event.packageName == packageName) return

        when (state) {
            State.AIMING -> {
                // Node bounds go stale as the list moves, and a tap against stale bounds
                // lands on the wrong message.
                handler.removeCallbacks(resnapshot)
                handler.postDelayed(resnapshot, RESNAPSHOT_DEBOUNCE_MS)
            }

            State.TRANSLATING, State.SHOWING -> {
                // Scrolling moves the message out from under the card, and leaving the app
                // makes it meaningless. Deliberately NOT dismissing on typeWindowContentChanged:
                // an incoming message or a delivery tick fires it and would rip the card away
                // mid-sentence.
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                ) {
                    removeCard()
                }
            }

            State.IDLE -> Unit
        }
    }

    override fun onInterrupt() {
        tearDown()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        tearDown()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        tearDown()
        serviceJob.cancel()
        instance = null
    }

    /** Every window we own, gone. A leaked overlay outlives the process. */
    private fun tearDown() {
        handler.removeCallbacksAndMessages(null)
        removeCard()
        endAiming()
        removeBubble()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Every captured bound belongs to the old layout, so anything anchored is now wrong.
        removeCard()
        endAiming()

        // Rotation moves the goalposts: pull the bubble back onto the screen.
        val params = bubbleParams ?: return
        params.x = params.x.coerceIn(0, maxBubbleX())
        params.y = params.y.coerceIn(0, maxBubbleY())
        windowManager?.updateViewLayout(bubble, params)
        saveBubblePosition(params)
    }

    // region Bubble

    private fun showBubble() {
        if (bubble != null) return
        val manager = windowManager ?: return

        val view = LayoutInflater.from(this).inflate(R.layout.samjho_bubble, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // NOT_FOCUSABLE is mandatory: a focusable overlay steals input focus and
            // the BolKe keyboard stops receiving keystrokes.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (prefs.bubbleX >= 0) prefs.bubbleX.coerceAtMost(maxBubbleX()) else maxBubbleX()
            y = if (prefs.bubbleY >= 0) prefs.bubbleY.coerceAtMost(maxBubbleY()) else (maxBubbleY() * 0.4f).toInt()
        }

        view.alpha = IDLE_ALPHA
        view.setOnTouchListener(bubbleTouchListener(params))

        manager.addView(view, params)
        bubble = view
        bubbleParams = params
    }

    private fun removeBubble() {
        val view = bubble ?: return
        // removeViewImmediate, not removeView: a leaked accessibility overlay outlives
        // the process and needs a reboot to clear.
        runCatching { windowManager?.removeViewImmediate(view) }
        bubble = null
        bubbleParams = null
    }

    private fun bubbleTouchListener(params: WindowManager.LayoutParams): View.OnTouchListener {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var originX = 0
        var originY = 0
        var dragging = false

        return View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    originX = params.x
                    originY = params.y
                    dragging = false
                    view.animate().alpha(1f).setDuration(80).start()
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = (originX + dx).toInt().coerceIn(0, maxBubbleX())
                        params.y = (originY + dy).toInt().coerceIn(0, maxBubbleY())
                        windowManager?.updateViewLayout(view, params)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapToEdge(view, params)
                    } else {
                        onBubbleTapped()
                    }
                    view.animate().alpha(IDLE_ALPHA).setDuration(200).start()
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.animate().alpha(IDLE_ALPHA).setDuration(200).start()
                }
            }
            true
        }
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val max = maxBubbleX()
        params.x = if (params.x + view.width / 2 < max / 2) 0 else max
        windowManager?.updateViewLayout(view, params)
        saveBubblePosition(params)
    }

    private fun saveBubblePosition(params: WindowManager.LayoutParams) {
        prefs.bubbleX = params.x
        prefs.bubbleY = params.y
    }

    private fun onBubbleTapped() {
        // Unlike a Toast, this is not subject to background-toast limits on Android 11+.
        bubble?.let { view ->
            view.animate().scaleX(1.25f).scaleY(1.25f).setDuration(90).withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }.start()
        }

        when (state) {
            State.AIMING -> endAiming()
            State.TRANSLATING, State.SHOWING -> removeCard()
            State.IDLE -> beginAiming()
        }
    }

    // endregion

    // region Aim mode

    private fun beginAiming() {
        val manager = windowManager ?: return
        if (catcher != null) return

        targets = snapshot()
        if (targets.isEmpty()) {
            showCard(null, getString(R.string.samjho_no_text))
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.samjho_catcher, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                // rawX/rawY are screen coordinates, the same space getBoundsInScreen uses.
                onScreenTapped(event.rawX.toInt(), event.rawY.toInt())
            }
            true
        }

        manager.addView(view, params)
        catcher = view
        state = State.AIMING
        handler.postDelayed(exitAim, AIM_TIMEOUT_MS)
    }

    private fun endAiming() {
        handler.removeCallbacks(exitAim)
        handler.removeCallbacks(resnapshot)
        val view = catcher ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
        catcher = null
        targets = emptyList()
        if (state == State.AIMING) state = State.IDLE
    }

    private fun onScreenTapped(x: Int, y: Int) {
        val target = ScreenText.hitTest(targets, x, y)
        // Read the neighbours before endAiming() clears the snapshot.
        val context = if (target == null) emptyList() else contextAround(target)
        endAiming()
        if (target == null) return // tapped empty space: leave her where she was

        showCard(target, getString(R.string.samjho_translating))
        state = State.TRANSLATING

        serviceScope.launch {
            val result = translator.translate(target.text, context)
            if (state != State.TRANSLATING) return@launch // she dismissed it while waiting
            when (result) {
                is TranslationResult.Ok -> updateCard(target, result.text)
                is TranslationResult.Failed -> updateCard(target, getString(errorText(result.error)))
            }
        }
    }

    /**
     * The messages around the tapped one, top to bottom. This is what turns
     * "he said he'll come tomorrow" into something that names who.
     */
    private fun contextAround(target: TextTarget): List<String> {
        val ordered = targets.sortedBy { it.top }
        val index = ordered.indexOf(target)
        if (index < 0) return emptyList()
        val from = (index - CONTEXT_SPAN).coerceAtLeast(0)
        val to = (index + CONTEXT_SPAN).coerceAtMost(ordered.lastIndex)
        return ordered.subList(from, to + 1).map { it.text }
    }

    private fun errorText(error: TranslationError): Int = when (error) {
        TranslationError.NO_KEY -> R.string.samjho_error_no_key
        TranslationError.NO_NETWORK -> R.string.samjho_error_network
        TranslationError.API_ERROR -> R.string.samjho_error_api
    }

    private fun snapshot(): List<TextTarget> =
        ScreenText.capture(rootInActiveWindow, packageName)

    // endregion

    // region Card

    /** [anchor] is the message that was tapped, or null to float the card unanchored. */
    private fun showCard(anchor: TextTarget?, text: String) {
        removeCard()
        val manager = windowManager ?: return

        val view = LayoutInflater.from(this).inflate(R.layout.samjho_card, null)
        view.findViewById<TextView>(R.id.samjho_card_text).text = text

        val margin = dp(12)
        val screenWidth = resources.displayMetrics.widthPixels
        val width = if (anchor == null) {
            screenWidth - margin * 2
        } else {
            anchor.width().coerceIn(dp(220), screenWidth - margin * 2)
        }

        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                // NOT_TOUCH_MODAL is deprecated on API 30+ where it is the default, but
                // minSdk here is 26 and without it taps outside never reach the app below.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (anchor?.left ?: margin)
                .coerceIn(margin, (screenWidth - width - margin).coerceAtLeast(margin))
            y = anchor?.let { it.bottom + dp(4) } ?: (resources.displayMetrics.heightPixels / 3)
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                // Delivered because of FLAG_WATCH_OUTSIDE_TOUCH. The touch still reaches the
                // app underneath, so her tap does its normal job as well as closing the card.
                MotionEvent.ACTION_OUTSIDE, MotionEvent.ACTION_DOWN -> removeCard()
            }
            false
        }

        manager.addView(view, params)
        card = view
        cardParams = params
        state = State.SHOWING

        // Messages sit at the bottom of a chat, so the card usually wants to go above them.
        // Height is only known after layout.
        view.post { placeCard(anchor, view, params) }

        handler.removeCallbacks(dismissCard)
        handler.postDelayed(dismissCard, CARD_TIMEOUT_MS)
    }

    /** Swap the loading line for the result, and re-place the card now that it is taller. */
    private fun updateCard(anchor: TextTarget?, text: String) {
        val view = card ?: return
        val params = cardParams ?: return
        view.findViewById<TextView>(R.id.samjho_card_text).text = text
        state = State.SHOWING

        // The result is taller than the loading line, so re-place rather than leave it
        // hanging off the bottom.
        view.post { placeCard(anchor, view, params) }

        handler.removeCallbacks(dismissCard)
        handler.postDelayed(dismissCard, CARD_TIMEOUT_MS)
    }

    /**
     * Below the message, or above it when there is no room. Computed from the absolute
     * anchor every time, so repeated calls never compound.
     */
    private fun placeCard(
        anchor: TextTarget?,
        view: View,
        params: WindowManager.LayoutParams
    ) {
        if (anchor == null || card !== view) return
        val margin = dp(12)
        val gap = dp(4)
        val screenHeight = resources.displayMetrics.heightPixels

        val below = anchor.bottom + gap
        val target = if (below + view.height <= screenHeight - margin) {
            below
        } else {
            (anchor.top - view.height - gap).coerceAtLeast(margin)
        }

        if (target == params.y) return
        params.y = target
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun removeCard() {
        handler.removeCallbacks(dismissCard)
        val view = card ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
        card = null
        cardParams = null
        state = State.IDLE
    }

    // endregion

    // region Geometry

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun bubbleSize(): Int = bubble?.width?.takeIf { it > 0 }
        ?: (44 * resources.displayMetrics.density).toInt()

    private fun maxBubbleX(): Int =
        (resources.displayMetrics.widthPixels - bubbleSize()).coerceAtLeast(0)

    private fun maxBubbleY(): Int =
        (resources.displayMetrics.heightPixels - bubbleSize()).coerceAtLeast(0)

    // endregion

    companion object {
        private const val IDLE_ALPHA = 0.55f
        private const val AIM_TIMEOUT_MS = 10_000L
        private const val CARD_TIMEOUT_MS = 20_000L
        private const val RESNAPSHOT_DEBOUNCE_MS = 150L

        /** Messages either side of the tapped one, sent to the model as context. */
        private const val CONTEXT_SPAN = 4

        /**
         * The running service, if the user has it enabled. An accessibility service is a
         * singleton owned by the system, so this is how the settings screen reaches it.
         */
        private var instance: SamjhoService? = null

        /** Called after Save so the bubble appears or disappears without a toggle round-trip. */
        fun onSettingsChanged(context: Context) {
            val service = instance ?: return
            if (PreferencesManager(context).isSamjhoBubbleEnabled) {
                service.showBubble()
            } else {
                service.removeBubble()
            }
        }
    }
}
