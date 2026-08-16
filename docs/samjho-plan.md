# Samjho — implementation plan

Samjho (ਸਮਝੋ) is a floating bubble that translates any English text on screen into
Gurmukhi Punjabi, in place, in any app. It ships inside the BolKe APK as a second
service. It shares nothing with the keyboard except SharedPreferences and colors.

## Target flow

1. She sees an English message in WhatsApp.
2. She taps the Samjho bubble, parked on the screen edge.
3. The screen dims very slightly. A hint reads "ਮੈਸੇਜ ਤੇ ਟੈਪ ਕਰੋ".
4. She taps the message.
5. A Gurmukhi card covers that message.
6. She scrolls, or taps anywhere else. The card disappears and the original is back.

Output is Gurmukhi only. No language picker, no script toggle.

## Why the tap is caught by us, not by WhatsApp

`TYPE_VIEW_CLICKED` does not fire for views without a click listener, and WhatsApp
message bubbles are plain non-clickable TextViews. An implementation that waits for
a click event gets nothing and step 4 silently does nothing.

Instead:

- On bubble tap, walk `getRootInActiveWindow()` once and record every visible text
  node as `(getBoundsInScreen(), text)`.
- Put a fullscreen transparent `TYPE_ACCESSIBILITY_OVERLAY` window over everything.
- Her tap lands on our window. We read `event.rawX/rawY` directly.
- Hit-test those coordinates against the recorded rects. Smallest containing rect wins.

Accessibility overlays are trusted windows, so they are exempt from Android's
untrusted-touch restrictions and can be transparent and touchable at the same time.
`TYPE_ACCESSIBILITY_OVERLAY` needs no "draw over other apps" permission, so enabling
the accessibility service is the only setup step.

## Files

```
samjho/SamjhoService.kt          accessibility service: bubble, aim mode, card, state machine
samjho/ScreenText.kt             tree walk, Gurmukhi detection, hit-test
samjho/PunjabiTranslator.kt      Gemini call + LRU cache
res/layout/samjho_bubble.xml     the bubble circle
res/layout/samjho_card.xml       the translation card
res/xml/samjho_service.xml       accessibility service config
```

Modified: `AndroidManifest.xml`, `PreferencesManager.kt`, `SettingsActivity.kt`,
`activity_settings.xml`, `strings.xml`, `colors.xml`.

No new Gradle dependencies. The Gemini call reuses the `HttpURLConnection` pattern
already in `translation/LanguageServiceClient.kt`. ML Kit is not an option here — its
on-device translator supports 87 languages and Punjabi is not one of them.

## State machine

One field drives everything. Without it the service does tree walks on every
`typeWindowContentChanged` event, which fires constantly and drains the battery.

```
IDLE        bubble only. onAccessibilityEvent returns immediately.
AIMING      snapshot taken, catcher window up, waiting for her tap.
TRANSLATING card up showing a loading line.
SHOWING     card up with the translation.
```

`onAccessibilityEvent` early-returns on `IDLE`. This is the single most important
performance decision in the feature.

---

# Phase 0 — Settings and permission plumbing

No service yet. Everything here is verifiable on its own.

**`PreferencesManager.kt`** — four additions:

```
geminiApiKey: String        ""
isSamjhoBubbleEnabled: Bool false
bubbleX / bubbleY: Int      last parked position
```

**`SettingsActivity`** — a "ਸਮਝੋ" section appended to the existing scroll view.
No tabs, no `MainActivity`, no fragments, no ViewPager2. The screen is already a
list of settings sections and this is one more.

- Status line: whether the accessibility service is on, read from
  `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` — the same technique
  `SetupActivity.isKeyboardEnabled()` already uses for the IME.
- "ਸੈਟਿੰਗਾਂ ਖੋਲ੍ਹੋ" button opening `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
  A service cannot enable itself; this is a deep link, nothing more.
- API key field.
- Bubble on/off switch. This hides the bubble without touching the accessibility
  permission, so she can silence it without going back into system settings.
- Plain-language note on what leaves the phone and when.

**Verify:** toggle the service in system settings, come back, status line is correct.
Key persists across app restarts.

---

# Phase 1 — The bubble

**`res/xml/samjho_service.xml`**

```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:description="@string/samjho_service_description" />
```

**Manifest:** service guarded by `BIND_ACCESSIBILITY_SERVICE`, intent-filter
`android.accessibilityservice.AccessibilityService`, meta-data pointing at the config.

**`SamjhoService.onServiceConnected()`** inflates the bubble and adds it through
`WindowManager`:

```
type    = TYPE_ACCESSIBILITY_OVERLAY
flags   = FLAG_NOT_FOCUSABLE
format  = TRANSLUCENT
gravity = TOP or START
```

`FLAG_NOT_FOCUSABLE` is mandatory. Without it the bubble steals input focus and
breaks the BolKe keyboard.

Drag: one touch listener. Compare travel against `ViewConfiguration.scaledTouchSlop`
to tell a tap from a drag, update `layoutParams.x/y` on move, snap to the nearest
edge on release, save the position to prefs.

Visual: 44dp circle, "ਬ" in accent blue on the dark key background. No emoji.
Idle alpha 0.55 so it does not obscure content, full alpha while touched.

**Verify:** bubble appears over every app including the launcher, drags and snaps,
returns to its parked spot after a reboot, and the keyboard still types normally
while it is on screen.

---

# Phase 2 — Aim mode, snapshot, hit-test

The whole interaction, working, with no API key involved. The card shows the raw
English text it captured. If this phase is right, the rest is just replacing a string.

**`ScreenText.kt`**

```kotlin
data class TextTarget(val left: Int, val top: Int, val right: Int, val bottom: Int, val text: String)

fun capture(root: AccessibilityNodeInfo?, selfPackage: String): List<TextTarget>
fun hitTest(targets: List<TextTarget>, x: Int, y: Int): TextTarget?
fun isTranslatable(text: String): Boolean
```

Plain ints, not a `Rect`. `android.graphics.Rect` throws in JVM unit tests, so keeping
the geometry primitive is what lets the hit-test be checked without a device. `Rect` is
used only inside `capture`, where `getBoundsInScreen` demands it.

`capture` walks the tree iteratively with a node budget (2000) and collects a node when:

- `isVisibleToUser` is true,
- `text` (falling back to `contentDescription` — WhatsApp puts the message and
  timestamp there on some rows) is not blank,
- bounds have non-zero area,
- the text contains at least one Latin letter,
- the text is not already mostly Gurmukhi (`U+0A00`–`U+0A7F`),
- the package is not our own.

`hitTest` returns the smallest rect containing the point, which is the most specific
node under her finger.

**Aim mode** on bubble tap: capture, show the hint, add the catcher window
(`MATCH_PARENT`, `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_FOCUSABLE`, `#14000000`
scrim so the mode is visible). Its touch listener reads `rawX/rawY` on `ACTION_DOWN`,
removes itself, and hit-tests. A miss exits quietly. A 10s timeout also exits.

Node bounds go stale during scrolling and animation, and a tap mid-animation lands on
the wrong rect. While `AIMING`, re-capture on `typeViewScrolled` and
`typeWindowContentChanged`, debounced 150ms.

**Verify:** aim, tap a WhatsApp message, the card shows exactly that message's text.
Repeat in Gmail and SMS. Tap blank space, aim mode exits with nothing shown.

---

# Phase 3 — Translation

**`PunjabiTranslator.kt`**

```kotlin
suspend fun translate(target: String, context: List<String>): Result<String>
```

POST to `generativelanguage.googleapis.com/v1beta/models/<model>:generateContent`
with an `x-goog-api-key` header, 8s connect and read timeouts, on `Dispatchers.IO`.
Model id lives in one constant. Set `thinkingConfig.thinkingBudget = 0` — the 2.5
Flash models think by default, which adds seconds to a latency-critical interaction.

The tree walk already collected every message on screen, so the surrounding messages
cost nothing to include:

```
You are translating for a Punjabi woman who reads Gurmukhi but not English.
Translate ONLY the message marked <<<TARGET>>> into simple, natural Punjabi
in Gurmukhi script.
- Convey meaning and tone, not word by word.
- Everyday spoken Punjabi, the way a family member would explain it.
- Keep names, numbers, amounts, dates, OTPs and links exactly as they are.
- Output only the translation. No quotes, no explanation, no English.
```

This is what fixes the problem that started this: her old app translated one sentence
in isolation, so "he said he'll come tomorrow" never resolved to who. Eight lines of
context and it does.

**Cache:** a `LinkedHashMap` in access order with `removeEldestEntry` capped at 200,
persisted to SharedPreferences as JSON. Roughly twenty lines. It earns its place on
re-reads — scrolling back over a message already translated — not on new messages,
which are new by definition.

Skipped: the 150-phrase dictionary. It exact-matches whole messages, and real messages
are sentences, so the hit rate is near zero. Skipped: SQLite and 90-day eviction. At
1,500 free requests a day against her thirty, there is no cost to optimize.

**Verify:** the Test button in settings translates a known sentence. Tap a real
message, get Punjabi. Tap it again, it returns instantly from cache.

---

# Phase 4 — The card and its dismissal

Positioned at the message: `x = bounds.left`, `y = bounds.bottom + 4dp`, flipped above
the message when it would fall off the bottom, width clamped between 220dp and the
screen width less margins.

States, in order of appearance: a loading line ("ਅਨੁਵਾਦ ਹੋ ਰਿਹਾ ਹੈ...") shown
immediately so the tap feels answered, then the translation at 18sp, or an error
naming the actual cause — no internet, no API key, or nothing readable on this screen.

**Dismissal.** Nothing is restored when the card goes away; the card is a separate
window sitting on top, so removing it reveals the untouched original underneath.

| Trigger | Mechanism |
| --- | --- |
| Taps anywhere else | `FLAG_WATCH_OUTSIDE_TOUCH` on the card window delivers `ACTION_OUTSIDE`. The touch still reaches the app underneath, so her tap does its normal job as well. Needs `FLAG_NOT_TOUCH_MODAL` too — deprecated and default from API 30, but minSdk here is 26. |
| Scrolls | `typeViewScrolled` while `SHOWING`. The message has moved, so the card must go. |
| Leaves the app | `typeWindowStateChanged`. |
| Taps the card | Its own touch listener. |
| Walks away | 20s timeout as a backstop. |

**Not** `typeWindowContentChanged`. It fires on every delivery tick and every incoming
message, and dismissing on it rips the card away while she is still reading. It is used
only during `AIMING`, where re-snapshotting is cheap and harmless.

Built in phase 2 rather than here — a card that cannot be dismissed makes phase 2
untestable, and writing a throwaway dismissal first would have been more work than
writing the real one. What is left for this phase is the loading and error states and
the positioning edge cases.

**Verify:** each row of that table, by hand, in WhatsApp. Especially scroll — the card
must never lag behind the message it belongs to.

---

# Phase 5 — Hardening

- `dismissAll()` calling `removeViewImmediate` for every window, invoked from
  `onInterrupt`, `onUnbind` and `onDestroy`. Window leaks from an accessibility
  service survive the app and need a reboot to clear.
- Service-scoped `SupervisorJob` cancelled in `onDestroy`, matching `BolKeIMEService`.
- Rotation and screen size change: dismiss everything, clamp the bubble back on screen.
- `FLAG_SECURE` apps (banking) expose no node text. Say so plainly rather than
  failing silently.
- Confirm `IDLE` really does early-return, by checking battery attribution after an
  hour of normal use with the service on.

Known ceilings, marked with `ponytail:` comments where they live in code:

- Text inside images is invisible to the node tree. The fix is ML Kit text recognition
  on `takeScreenshot()`, worth adding only if she actually hits it.
- Split-screen and multi-window are untested; screen coordinates and node bounds may
  disagree there.
- The cache is capped at 200 entries with no expiry.

---

# Test

One unit test, on the only pure logic in the feature: `hitTest` picking the smallest
containing rect, and the Gurmukhi detector not sending already-Punjabi text to the API.
Everything else needs a device.

# Privacy, stated accurately

The tapped message and the messages visible around it are sent to Google's Gemini API,
only at the moment she taps, and only from that one screen. Translations are cached on
the device. Nothing is logged or uploaded anywhere else. The settings screen says
exactly this and does not claim more.
