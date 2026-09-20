# TunnelVision

TunnelVision is a symbol-focused reading mode for IntelliJ IDEA. It dims code outside the symbol under the caret and its local references, keeping useful context visible.

## Features

- Semantic PSI matching for Java, Kotlin, JavaScript, and TypeScript.
- Whole-file lexical word matching, including comments and strings.
- Static focus or dynamic retargeting as the caret moves.
- Configurable symbol, line, statement, and scope-head context.
- Next/previous match navigation with wraparound.
- Native editor color-scheme settings.

## Requirements

- IntelliJ IDEA 2025.2 or newer.
- JavaScript and TypeScript support requires IntelliJ IDEA Ultimate's bundled JavaScript plugin.

## Use

1. Place the caret on a symbol and invoke **Toggle Focus** from the editor context menu.
2. Use **Next Match** (`Ctrl+Alt+Down`) and **Previous Match** (`Ctrl+Alt+Up`) to navigate occurrences.
3. Configure behavior in **Settings | Tools | TunnelVision** and colors in **Settings | Editor | Color Scheme | TunnelVision**.

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
