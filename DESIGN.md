---
name: BolKe
description: A calm, trustworthy Punjabi voice and understanding keyboard for families.
colors:
  keyboard-bg: "#10131A"
  keyboard-surface: "#252B36"
  keyboard-text: "#F7F8FC"
  keyboard-muted: "#B5BFCE"
  app-bg: "#F4F6F8"
  app-surface: "#FFFFFF"
  app-text: "#1B1C1E"
  app-muted: "#5F6368"
  primary: "#0B57D0"
  primary-soft: "#D8E6FE"
  success-soft: "#C7EED4"
  warning-soft: "#FFE3B3"
typography:
  headline:
    fontFamily: "sans-serif"
    fontSize: "32sp"
    fontWeight: 700
    lineHeight: 1.2
  title:
    fontFamily: "sans-serif"
    fontSize: "16sp"
    fontWeight: 700
    lineHeight: 1.3
  body:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: "sans-serif"
    fontSize: "12sp"
    fontWeight: 600
    lineHeight: 1.3
rounded:
  control: "12dp"
  pill: "999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "16dp"
  lg: "22dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.app-surface}"
    rounded: "{rounded.pill}"
    height: "58dp"
    padding: "0 20dp"
  button-secondary:
    backgroundColor: "{colors.primary-soft}"
    textColor: "{colors.primary}"
    rounded: "{rounded.pill}"
    height: "50dp"
    padding: "0 18dp"
  keyboard-key:
    backgroundColor: "{colors.keyboard-surface}"
    textColor: "{colors.keyboard-text}"
    rounded: "{rounded.control}"
    height: "48dp"
---

# Design System: BolKe

## Overview

**Creative North Star: "The Family Interpreter"**

BolKe should feel like a trusted family member sitting beside the user in bright, everyday surroundings: clear, patient, and ready to help without taking over. The keyboard is a restrained dark utility surface because it sits over many apps; setup and settings use a high-contrast light surface for comfortable reading.

The system rejects novelty-keyboard decoration, overlay-style controls, dense technical settings, and hidden primary actions. Visual hierarchy always serves one of two immediate jobs: speak to write, or copy to understand.

**Key Characteristics:**

- System typography with large Gurmukhi where meaning is the focus.
- Restrained blue used only for primary actions and selection.
- Tonal surfaces, not decorative shadows, establish hierarchy.
- At least 48dp touch targets and explicit text labels for important actions.

## Colors

The palette combines a near-black keyboard canvas with a neutral light app shell and one dependable blue accent.

### Primary

- **Trust Blue** (`#0B57D0`): primary actions, selected modes, and focus emphasis.
- **Soft Trust Blue** (`#D8E6FE`): secondary actions and selected containers.

### Neutral

- **Keyboard Night** (`#10131A`): keyboard background.
- **Raised Key** (`#252B36`): ordinary keys and dark-surface controls.
- **Clear White** (`#F7F8FC`): primary keyboard text.
- **App Mist** (`#F4F6F8`): setup and settings background.
- **App Surface** (`#FFFFFF`): readable content surfaces.
- **Near Ink** (`#1B1C1E`): primary app text.
- **Quiet Ink** (`#5F6368`): supporting text; do not use below WCAG AA contrast.

### Named Rules

**The One Accent Rule.** Blue signals action or selection; it is never scattered as decoration.

## Typography

**Display Font:** Android system sans-serif
**Body Font:** Android system sans-serif

**Character:** Familiar platform typography keeps controls recognizable and supports both Latin and Gurmukhi without mismatched font personalities.

### Hierarchy

- **Headline** (700, 28–32sp, 1.2): setup and settings page titles only.
- **Title** (700, 16sp, 1.3): section titles and important state labels.
- **Body** (400, 14–16sp, 1.45): instructions and explanations, capped near 70 characters where layout permits.
- **Translation** (400–600, 24sp minimum, 1.4): copied-message results in Gurmukhi.
- **Label** (600, 12sp, 1.3): short status labels; sentence case is preferred.

### Named Rules

**The Read-It-Once Rule.** Punjabi results must be large enough to understand without zooming and must never compete with secondary metadata.

## Elevation

BolKe is flat by default. Depth comes from tonal layering between background, surface, key, and selected state rather than broad shadows. Focus uses a clear outline or platform focus treatment, not glow.

### Named Rules

**The Flat-by-Default Rule.** Add elevation only when Android requires it to communicate an active overlay or pressed state; ordinary cards and keys remain tonal.

## Components

### Buttons

- **Shape:** full pill for primary and secondary actions; minimum 48dp height.
- **Primary:** Trust Blue with white text, 58dp height in setup/settings.
- **Focus / pressed:** Android state treatment plus a high-contrast focus indication.
- **Secondary:** Soft Trust Blue with Trust Blue text and an explicit action label.

### Chips

- **Style:** compact tonal surface with high-contrast text; use only for relevant family phrases or the two writing modes.
- **State:** selected state uses Trust Blue emphasis; secure fields show no chips.

### Cards / Containers

- **Corner Style:** 12–16dp, never exaggerated.
- **Background:** white or a single purposeful soft semantic tone.
- **Shadow Strategy:** no decorative shadow.
- **Internal Padding:** 16–22dp.

### Inputs / Fields

- **Style:** 52dp minimum height, tonal background, 12dp corners, system cursor and selection behavior.
- **Focus:** strong platform focus state with visible contrast.
- **Error / Disabled:** pair color with direct text; never communicate state by color alone.

### Navigation

Setup follows one ordered path: enable, select, grant microphone in context, choose copied-message translation, and try the two core tasks. Settings keep everyday controls above a clearly separated support section.

### Translation Panel

The panel temporarily replaces keys. It prioritizes one large Gurmukhi result, then **Copy Punjabi**, **Back to keyboard**, **Try another wording**, and a collapsed **Show original** action. Loading, offline, unsupported, and retry states occupy the same stable region.

## Do's and Don'ts

### Do:

- **Do** keep primary touch targets at least 48dp and translation text at least 24sp.
- **Do** use Android system typography, icons, focus behavior, sharing, and selected-text interfaces.
- **Do** hide voice, clipboard, translation, learned spellings, and family phrases in secure fields.
- **Do** use exact, non-technical privacy copy explaining when audio or copied text leaves the device.

### Don't:

- **Don't** use emojis, decorative controls, excessive cards, technical terminology, tiny labels, weak contrast, or hidden primary actions.
- **Don't** use overlay bubbles, screen capture, OCR, notification access, Accessibility screen reading, or decorative animation.
- **Don't** add colored side-stripe borders, gradient text, glassmorphism, or broad border-plus-shadow cards.
- **Don't** use a hardcoded bank-app list or interfere with a bank-owned secure keypad.
