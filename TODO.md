# Plug-and-play and additive focus

## Agreed implementation

- [x] Provide theme-safe default dimming and a search-like symbol background.
- [x] Preserve syntax foreground in retained statements and highlighted symbols.
- [x] Respect explicit color-scheme overrides, including empty styles.
- [x] Default retained context to statement + symbol; leave other contexts configurable.
- [x] Store multiple independent tracks per editor with per-track static/dynamic mode.
- [x] Enable replaces tracks only after valid activation; Toggle enables/disables.
- [x] Add preserves existing tracks and activates focus when inactive.
- [x] Pin adds a fixed track even when dynamic mode is configured.
- [x] Remove removes the track under the caret, otherwise the most recent track.
- [x] Disable clears all tracks, listeners, tasks, and highlighters.
- [x] Keep at most one dynamic track for the primary caret alongside static pins.
- [x] Dynamic movement to whitespace/unresolved symbols preserves the last valid target.
- [x] Deduplicate the same semantic target in the same scope, not same-spelled distinct symbols.
- [x] Dim outside the union of retained contexts; merge overlapping ranges.
- [x] Navigate the combined occurrences with next/previous and wraparound.
- [x] Shift all anchors after edits and reject stale results after edits, retargeting, removal, or disposal.
- [x] Validate defaults, overrides, additive overlap, duplicates, shadowing, dynamic pins, invalid activation, edits, navigation, and lifecycle.
- [x] Update README with actions and limitations.

## Reference and boundaries

Use ../tunnelvision.nvim behavior, not its implementation details. Remain native,
PSI-first, cancellable, and off the UI thread. Keep shared settings/colors initially.
Preserve current named-function PSI scope; do not silently fall back to lexical
matching. Related context means statements containing occurrences, not data-flow
relationships. Missing scheme fallback is a likely visual failure; the user's exact
installed theme has not been reproduced.

Defer per-track styling/options UI, multi-caret dynamic tracking, flow analysis,
whole-file PSI scope, additional languages, and unrelated structural improvements.

TODO.md is already tracked. Its local .git/info/exclude entry does not hide tracked
changes; untracking it is not part of this implementation.

## Validation completed

- Full Gradle test suite passed, including additive PSI/word tracking, duplicate
  identity and scope, overlapping context, same-line navigation, pin/dynamic
  coexistence, rapid queued additions, edits, removal, and stale callbacks.
- Missing-style regression failed against the original color resolver with a
  NullPointerException, then passed with theme-derived fallback styles.
- Explicit empty and per-editor color overrides remain respected; default symbol
  backgrounds preserve syntax foreground and font.
- `./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure`
  passed, including searchable-settings indexing and packaged plugin creation.
- `python3 tools/check.py` and `git diff --check` passed.
- Full cross-version IDE Plugin Verifier and a visual smoke test in the user's
  actual theme were not run. Named-function PSI limitations remain unchanged.

# Deferred work

- Add lambda-aware scope for Java and Kotlin.
- Add arrow-function and anonymous-function scope for JavaScript and TypeScript.
- Support Kotlin expression-bodied functions and complex trailing-lambda context.
- Handle Java anonymous classes and nested local classes.
- Support destructuring declarations and unusual declaration-name ranges.
- Support injected languages and mixed-language files.
- Support multiple carets.
- Add read/write-specific occurrence styling.
- Add flow/data-dependency mode.
- Add whole-file PSI scope and support additional IntelliJ-based IDEs.
- Add more languages through optional structural adapters.
