# TunnelVision

TunnelVision is a symbol-focused reading mode for IntelliJ IDEA. It dims code outside the symbol under the caret and its local references, keeping useful context visible.

## Features

- Semantic PSI matching for Java, Kotlin, JavaScript, and TypeScript.
- Whole-file lexical word matching, including comments and strings.
- Multiple static tracks, or dynamic caret tracking alongside fixed pins.
- Configurable symbol, line, statement, and scope-head context.
- Next/previous occurrence navigation across all tracks, with wraparound.
- Native editor color-scheme settings.

## Requirements

- IntelliJ IDEA 2025.2 or newer.
- JavaScript and TypeScript support requires IntelliJ IDEA Ultimate's bundled JavaScript plugin.

## Use

1. Place the caret on a symbol and invoke **Toggle Focus** from the editor context menu.
2. Move to another symbol and invoke **Add Focus** to track it without releasing existing focus.
3. Use **Next Match** (`Ctrl+Alt+Down`) and **Previous Match** (`Ctrl+Alt+Up`) to navigate all tracked occurrences.
4. Configure behavior in **Settings | Tools | TunnelVision** and colors in **Settings | Editor | Color Scheme | TunnelVision**.

No configuration is required: unrelated code is dimmed, lines containing matches keep
their syntax colors, and tracked symbols get a search-like background. Statement and
scope-head context are optional; colors remain editable in the native color-scheme settings.
Existing saved highlight-area choices remain unchanged when upgrading.

| Action | Behavior |
| --- | --- |
| **Enable Focus** | Replace existing tracks with the symbol under the caret. |
| **Add Focus** | Add a track using the configured mode; repeated targets are ignored. |
| **Pin Focus** | Add a fixed track even when dynamic mode is configured. |
| **Remove Focus** | Remove the track under the caret, otherwise the most recent track. |
| **Disable Focus** | Clear every track in the editor. |
| **Toggle Focus** | Enable when inactive; disable when active. |

Dynamic mode keeps one moving track alongside any fixed pins. Moving onto whitespace
or an unresolved symbol keeps its last valid target. Adding another dynamic target
replaces only the moving track, not the pins. Each track keeps the mode it was created
with; changing the configured mode affects new tracks. Other behavior settings and
colors apply to existing tracks. Failed Add/Enable operations preserve existing focus.
All actions are available through **Find Action**; Add, Pin, and Remove also appear in
the editor context menu.

`psi` is the default source: it resolves references within the enclosing named function. `word` matches the exact word throughout the complete file and intentionally includes comments and strings.

## Limitations

Focus scopes currently cover named functions. Lambda, anonymous-function, injected-language, multi-caret, and flow/data-dependency support are deferred; see [TODO.md](TODO.md).

## Development and releases

Run the test suite with:

```bash
./gradlew test
```

Release instructions are in [docs/publishing.md](docs/publishing.md).

## License

[MIT](LICENSE)
