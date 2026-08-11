# Design

## Source of truth
- Status: Active
- Last refreshed: 2026-06-06
- Primary product surfaces: R0DUMP Manager Android system app for configuring R0DUMP dump, checking status/export, browsing artifacts, and repairing dex files.
- Evidence reviewed:
  - `PRODUCT.md`
  - `packages/apps/R0DUMPManager/src/com/android/r0dumpmanager/MainActivity.java`
  - `packages/apps/R0DUMPManager/res/values/strings.xml`
  - `packages/apps/R0DUMPManager/res/values-zh-rCN/strings.xml`
  - `frameworks/base/core/java/android/app/ActivityThread.java`
  - `art/runtime/art_method.cc`

## Brand
- Personality: technical, calm, precise.
- Trust signals: visible target package, explicit strategy mask, clear path model, readable status file summary, background repair state.
- Avoid: one long unstructured settings page, decorative hacker aesthetics, raw log spam as the main UI, ambiguous storage wording.

## Product goals
- Goals:
  - Configure a controlled R0DUMP run from the device.
  - Make private working output vs public Download export understandable.
  - Let users scan exported artifacts and repair dex without UI freezes.
- Non-goals:
  - General file manager for Android/data.
  - Multi-target orchestration.
  - Full Material Components dependency inside the ROM tree.
- Success signals:
  - User can run: select app -> tune config (auto-saved) -> apply default/preset -> start dump -> refresh status/scan export -> repair dex.
  - Risky strategies are visually separated from original-compatible defaults.
  - Missing output tells the user whether they are waiting for export or using the wrong path.

## Personas and jobs
- Primary personas: reverse-engineering tester, ROM maintainer, R0DUMP integration developer.
- User jobs: configure one target, confirm runtime behavior, locate exported dump files, repair dex, capture actionable failure details.
- Key contexts of use: Pixel test device, LineageOS user/userdebug builds, adb/logcat nearby, repeated flash/test cycles.

## Information architecture
- Primary navigation: four bottom destinations.
- Core screens:
  1. 工作台 / Workbench: compact target app selector with system/global toggles, R0DUMP version, and disclaimer.
  2. 配置 / Config: collapsed output/timing, safe defaults, range limits, experimental strategy selection, immediate save, and start/stop dump actions.
  3. 状态 / Status: inactive skeleton before dump starts, background status refresh, bottom floating refresh/scan actions, one-second auto-refresh while monitoring an active dump, and automatic handoff to Repair when a terminal status is observed while still on this page.
  4. 修复 / Repair: merged artifact browser plus selected-app repair plan, long-path-safe ZIP output row, terse empty states, background repair action, conditional progress state, and bottom floating scan/repair actions.
- Content hierarchy: safe workflow first, risky strategy groups separated, repair isolated from configuration.

## Design principles
- Pipeline clarity beats compactness.
- Reuse the FolkPatch-style Compose component vocabulary instead of keeping parallel Java View widgets.
- Every path shown must say whether it is a working path or public export path.
- Long-running work reports a start and finish state and never blocks the main thread.

## Visual language
- Color: restrained system-light utility palette, accent only for native selected/primary states.
- Typography: platform sans, bold section titles, compact helper text.
- Spacing/layout rhythm: 12dp outer padding, larger breaks before workflow sections, compact action rows.
- Shape/radius/elevation: flat Material surfaces, light bordered panels for explanatory/status blocks, transparent fixed action rows with opaque buttons only, no drop shadows, no nested cards.
- Motion: restrained state motion only: local 180ms language crossfade, native follow-finger horizontal pager between destinations, and a measured bottom dock that hides on vertical drag while fixed action buttons follow the dock progress, then settle at the bottom and remain usable; no system-locale relaunch, page reflow, or decorative choreography.
- Imagery/iconography: app icon only.

## Components
- Existing components to reuse: local Compose ports of FolkPatch `SplicedColumnGroup`, `ExpressiveCard`, `ExpressiveSwitch`, settings rows, toggle cards, warning cards, and rounded bottom navigation.
- New/changed components: four-destination bottom navigation, compact dropdown target picker, collapsed config groups, grouped strategy cards, merged artifact/repair page, repair progress card.
- Variants and states: selected destination, expanded/collapsed config and strategy groups, disabled repair action while running, not-refreshed/missing/loaded/dumping status states.
- Token/component ownership: Compose UI in `R0DumpComposeUi.kt`; backend/config/repair bridge in `MainActivity.java`; localized backend strings under `res/values*`.

## Accessibility
- Target standard: Android platform-default semantics and focus order.
- Keyboard/focus behavior: controls remain in source order by workflow stage.
- Contrast/readability: dark text on light utility panels; no low-contrast placeholder-as-label patterns for critical fields.
- Screen-reader semantics: buttons and checkboxes use explicit localized labels.
- Reduced motion and sensory considerations: no required motion.

## Responsive behavior
- Supported breakpoints/devices: phone portrait first; landscape remains scrollable.
- Layout adaptations: vertical stacking, action rows with equal weights.
- Touch/hover differences: touch-first default Android controls.

## Interaction states
- Loading: repair logs background start and disables repair button.
- Empty: status page explains that export may not exist yet.
- Error: errors include path and throwable text.
- Success: status and repair completion include counts and output paths.
- Disabled: stop action writes `r0dump dump disabled via settings flag` but does not kill already running processes.
- Offline/slow network: not applicable.

## Content voice
- Tone: concise technical Chinese/English.
- Terminology: use “dump”, “脱壳点”, “工作目录”, “公开导出”, “修补”, and “force backfill” consistently.
- Microcopy rules: mention defaults, storage model, and risk level at the point of decision.

## Implementation constraints
- Framework/styling system: platform `Activity` hosting AndroidX Compose; no legacy Java View UI stack.
- Design-token constraints: keep one Compose theme/palette and avoid reintroducing parallel View styling.
- Performance constraints: repair runs off the main thread; status scanning should remain bounded to candidate output roots.
- Compatibility constraints: LineageOS 23 / Android 16 privileged system app; Settings.Global contract must match `ActivityThread` and ART R0DUMP patches.
- Test/screenshot expectations:
  - `m R0DUMPManager -j2` after UI changes on this workstation.
  - Manual smoke: open app, switch four destinations, select app, apply presets, confirm edits auto-save, refresh missing status, scan artifacts from Status/Repair, run repair with invalid input and confirm no ANR.

## Open questions
- [ ] Whether status should read live private working dirs through a system service instead of exported Download files / owner: maintainer / impact: real-time observability.
