package io.github.leolaurindo.tunnelvision.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** How the focused symbol is chosen. */
enum class FocusMode {
    /** Keep the symbol selected when focus was activated. */
    STATIC,

    /** Retarget after the caret moves. */
    DYNAMIC,
}

/** Where occurrences come from. */
enum class MatchSource {
    /** Semantic occurrences through PSI, restricted to [FocusScope]. */
    PSI,

    /** Lexical occurrences of the word at the caret, always across the whole file. */
    WORD,
}

/** How far a semantic search reaches. */
enum class FocusScope {
    FUNCTION,
}

/** Independently styled highlight areas, from broadest to narrowest. */
enum class HighlightArea {
    SCOPE_HEAD,
    STATEMENT,
    LINE,
    SYMBOL;

    /** Label the settings and color pages show for this area. */
    val displayName: String
        get() = when (this) {
            SCOPE_HEAD -> "Scope head"
            STATEMENT -> "Statement"
            LINE -> "Line"
            SYMBOL -> "Symbol"
        }
}

@State(name = "TunnelVisionSettings", storages = [Storage("tunnelvision.xml")])
class TunnelVisionSettings : PersistentStateComponent<TunnelVisionSettings.State> {

    data class State(
        var mode: FocusMode = FocusMode.STATIC,
        var source: MatchSource = MatchSource.PSI,
        var scope: FocusScope = FocusScope.FUNCTION,
        var debounceMillis: Int = DEFAULT_DEBOUNCE_MILLIS,
        var maxFileLines: Int = DEFAULT_MAX_FILE_LINES,
        var areas: MutableList<HighlightArea> = mutableListOf(HighlightArea.LINE, HighlightArea.SYMBOL),
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        // Persisted files outlive enum values and sane bounds; never let one crash the IDE.
        state.areas.retainAll(HighlightArea.entries.toSet())
        state.debounceMillis = state.debounceMillis.coerceIn(0, MAX_DEBOUNCE_MILLIS)
        state.maxFileLines = state.maxFileLines.coerceAtLeast(MIN_MAX_FILE_LINES)
        this.state = state
    }

    fun isAreaEnabled(area: HighlightArea): Boolean = area in state.areas

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 150
        const val DEFAULT_MAX_FILE_LINES = 6_000
        private const val MAX_DEBOUNCE_MILLIS = 5_000
        private const val MIN_MAX_FILE_LINES = 100

        fun getInstance(): TunnelVisionSettings =
            ApplicationManager.getApplication().getService(TunnelVisionSettings::class.java)
    }
}
