# Project Guidance

- Use `~/projects/tunnelvision.nvim` as the behavior and terminology reference. Port behavior, not Neovim implementation details.
- Follow `PLAN.md`; keep the IntelliJ implementation small, native, and PSI-first.
- Target IntelliJ IDEA with Java, Kotlin, and JavaScript/TypeScript plugin integrations. Isolate language-specific PSI code behind small adapters.
- Do not bundle parsers or language servers. `psi` is the semantic source; `word` is the whole-file lexical source.
- Keep PSI work cancellable and off the UI thread. Dispose editor listeners and highlighters correctly.
- Prefer focused tests for shared behavior and per-language fixtures for structural differences.
- Follow YAGNI and DRI.
