package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.leolaurindo.tunnelvision.settings.FocusMode
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import io.github.leolaurindo.tunnelvision.settings.TunnelVisionSettings
import io.github.leolaurindo.tunnelvision.ui.FocusNotifier

class FocusSessionTest : BasePlatformTestCase() {

    private lateinit var settings: TunnelVisionSettings
    private lateinit var debouncer: ManualDebouncer
    private lateinit var computation: RecordingComputation

    override fun setUp() {
        super.setUp()
        settings = TunnelVisionSettings()
        debouncer = ManualDebouncer()
        computation = RecordingComputation()
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                void run() {
                    int count = 0;
                    System.out.println(count);
                }
            }
            """.trimIndent(),
        )
    }

    fun testStartAnchorsFocusAtTheCaretAndComputesOnce() {
        val session = startSession(FocusMode.STATIC)

        assertEquals(1, computation.requests.size)
        assertEquals(session.state.tracks.single().anchorOffset, computation.requests.single().caretOffset)
        assertEquals(0, debouncer.scheduleCount)
    }

    fun testCaretMovementRetargetsAfterDebounceInDynamicMode() {
        startSession(FocusMode.DYNAMIC)

        moveCaretTo(11)
        moveCaretTo(23)

        assertEquals(2, debouncer.scheduleCount)
        // The second move superseded the first: one refresh is left to run.
        assertEquals(1, debouncer.cancelCount)
        assertEquals(settings.state.debounceMillis, debouncer.delayMillis)
        assertEquals(1, computation.requests.size)

        debouncer.fire()
        assertEquals(3, computation.requests.size)
        assertEquals(23, computation.requests.last().caretOffset)
    }

    fun testStaticModeKeepsTheAnchorAcrossCaretMovement() {
        val session = startSession(FocusMode.STATIC)

        moveCaretTo(23)

        assertEquals(0, debouncer.scheduleCount)
        assertEquals(1, computation.requests.size)
        // The caret moved to 23 but focus stayed anchored where it was activated.
        assertEquals(0, session.state.tracks.single().anchorOffset)
        assertEquals(computation.requests.single().caretOffset, session.state.tracks.single().anchorOffset)
    }

    fun testDocumentChangesRefreshWithoutRetargeting() {
        val session = startSession(FocusMode.STATIC)

        myFixture.type("x")
        myFixture.type("y")

        assertEquals(2, debouncer.scheduleCount)
        assertEquals(1, debouncer.cancelCount)

        debouncer.fire()
        assertEquals(2, computation.requests.size)
        assertEquals(session.state.tracks.single().anchorOffset, computation.requests.last().caretOffset)
    }

    fun testDisposalDetachesListenersAndStopsRefreshing() {
        val session = startSession(FocusMode.DYNAMIC)

        session.dispose()

        assertTrue(session.state.isDisposed)
        moveCaretTo(11)
        myFixture.type("x")
        session.refreshNow()

        assertEquals(0, debouncer.scheduleCount)
        assertEquals(1, computation.requests.size)
    }

    fun testResultArrivingAfterADisposalIsDropped() {
        val runner = DeferredRunner()
        val session = startSession(FocusMode.DYNAMIC, runner)

        session.dispose()
        runner.deliver(FocusResult.Matched("count", FocusAreas.of(listOf(TextRange(1, 2)))))

        assertNull(session.state.result)
    }

    fun testStaticAnchorStaysOnItsSymbolWhenTextIsInsertedAboveIt() {
        val symbolOffset = myFixture.editor.document.text.indexOf("count")
        myFixture.editor.caretModel.moveToOffset(symbolOffset)
        val session = startSession(FocusMode.STATIC)
        val inserted = "// a header line\n"

        insertAt(0, inserted)
        debouncer.fire()

        // Focus still points at `count`, which the edit only moved further down.
        assertEquals(symbolOffset + inserted.length, session.state.tracks.single().anchorOffset)
        assertEquals(symbolOffset + inserted.length, computation.requests.last().caretOffset)
    }

    fun testStaticAnchorFollowsTextTypedInFrontOfItsSymbol() {
        val symbolOffset = myFixture.editor.document.text.indexOf("count")
        myFixture.editor.caretModel.moveToOffset(symbolOffset)
        val session = startSession(FocusMode.STATIC)

        insertAt(symbolOffset, " ")

        assertEquals(symbolOffset + 1, session.state.tracks.single().anchorOffset)
    }

    fun testStaticAnchorStaysOnItsSymbolWhenTextIsDeletedAboveIt() {
        val symbolOffset = myFixture.editor.document.text.indexOf("count")
        myFixture.editor.caretModel.moveToOffset(symbolOffset)
        val session = startSession(FocusMode.STATIC)
        val deletedAbove = "class Sample {\n"

        deleteRange(deletedAbove)

        // The anchored word is untouched and has only moved up with the text around it.
        assertEquals(symbolOffset - deletedAbove.length, session.state.tracks.single().anchorOffset)
    }

    fun testStaticAnchorLandsOnTheEditWhenItsOwnTextIsDeleted() {
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("count"))
        val session = startSession(FocusMode.STATIC)
        val declaration = "int count = 0;\n"
        val deletedAt = myFixture.editor.document.text.indexOf(declaration)

        deleteRange(declaration)

        // The anchored word itself is gone, so focus falls back to where the edit began.
        assertEquals(deletedAt, session.state.tracks.single().anchorOffset)
    }

    private fun insertAt(offset: Int, text: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(offset, text)
        }
    }

    private fun deleteRange(text: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = myFixture.editor.document
            val start = document.text.indexOf(text)
            document.deleteString(start, start + text.length)
        }
    }

    fun testResultInFlightWhenTheDocumentChangesIsRejectedBeforeTheDebounceFires() {
        val runner = DeferredRunner()
        val session = startSession(FocusMode.STATIC, runner)

        myFixture.type("x")
        runner.deliver(FocusResult.Matched("before the edit", FocusAreas.of(listOf(TextRange(1, 2)))))

        assertNull("the edit already invalidated the in-flight result", session.state.result)
    }

    fun testResultInFlightWhenTheCaretMovesIsRejectedInDynamicMode() {
        val runner = DeferredRunner()
        val session = startSession(FocusMode.DYNAMIC, runner)

        moveCaretTo(11)
        runner.deliver(FocusResult.Matched("old anchor", FocusAreas.of(listOf(TextRange(1, 2)))))

        assertNull("the moved anchor already invalidated the in-flight result", session.state.result)
    }

    fun testStaticAnchorKeepsAcceptingResultsWhileTheCaretMoves() {
        val runner = DeferredRunner()
        val session = startSession(FocusMode.STATIC, runner)

        moveCaretTo(11)
        val result = FocusResult.Matched("count", FocusAreas.of(listOf(TextRange(1, 2))))
        runner.deliver(result)

        // A static anchor does not move, so the symbol under focus is still the selected one.
        assertSame(result, session.state.result)
    }

    fun testResultComputedBeforeAnEditIsRejected() {
        val runner = DeferredRunner()
        val session = startSession(FocusMode.STATIC, runner)

        // The edit makes the in-flight result describe text that is no longer there.
        myFixture.type("x")
        debouncer.fire()
        runner.deliver(FocusResult.Matched("before the edit", FocusAreas.of(listOf(TextRange(1, 2)))))

        assertNull(session.state.result)
    }

    fun testStaleResultsAreRejectedAfterANewerRequest() {
        val runner = DeferredRunner()
        val session = startSession(FocusMode.DYNAMIC, runner)

        session.refreshNow()

        val stale = FocusResult.Matched("stale", FocusAreas.of(listOf(TextRange(1, 2))))
        runner.deliver(stale)
        assertNull(session.state.result)

        val fresh = FocusResult.Matched("count", FocusAreas.of(listOf(TextRange(3, 4))))
        runner.deliver(fresh)
        assertSame(fresh, session.state.result)
    }

    fun testRequestsTheAreasTheSettingsEnable() {
        startSession(
            FocusMode.STATIC,
            state = TunnelVisionSettings.State(mode = FocusMode.STATIC, areas = mutableListOf(HighlightArea.SYMBOL)),
        )

        assertEquals(setOf(HighlightArea.SYMBOL), computation.requests.single().areas)
    }

    fun testDoesNotReportTheOrdinaryUnavailabilityOfACaretBetweenSymbols() {
        val notifier = RecordingNotifier()
        val session = startSession(FocusMode.DYNAMIC, notifier = notifier)
        computation.unavailableReason = "no symbol at the caret"
        computation.unavailableReported = false

        session.refreshNow()

        assertTrue(session.state.result is FocusResult.Unavailable)
        assertEquals(emptyList<String>(), notifier.reasons)
    }

    fun testReportsAnUnavailableSourceOnceAndAgainAfterItRecovers() {
        val notifier = RecordingNotifier()
        val session = startSession(FocusMode.STATIC, notifier = notifier)
        computation.unavailableReason = "no symbol at the caret"

        session.refreshNow()
        session.refreshNow()

        assertEquals(listOf("no symbol at the caret"), notifier.reasons)

        computation.unavailableReason = null
        session.refreshNow()

        computation.unavailableReason = "no named function encloses count"
        session.refreshNow()

        assertEquals(
            listOf("no symbol at the caret", "no named function encloses count"),
            notifier.reasons,
        )
    }

    fun testReportsTheLargeFileGuard() {
        val notifier = RecordingNotifier()
        configureLongFile(lines = 100)

        startSession(
            FocusMode.STATIC,
            state = TunnelVisionSettings.State(maxFileLines = 100),
            notifier = notifier,
        )

        assertEquals(1, notifier.reasons.size)
        assertTrue(notifier.reasons.single(), notifier.reasons.single().contains("above the configured limit"))
    }

    fun testNavigationMovesTheCaretBetweenMatchedLinesAndWrapsAround() {
        val occurrences = occurrenceRanges()
        computation.occurrences = occurrences
        val session = startSession(FocusMode.STATIC)
        myFixture.editor.caretModel.moveToOffset(0)

        assertTrue(session.jumpToMatch(forward = true))
        assertEquals(occurrences[0].startOffset, myFixture.editor.caretModel.offset)

        assertTrue(session.jumpToMatch(forward = true))
        assertEquals(occurrences[1].startOffset, myFixture.editor.caretModel.offset)

        // Wraps around at the last match.
        assertTrue(session.jumpToMatch(forward = true))
        assertEquals(occurrences[0].startOffset, myFixture.editor.caretModel.offset)

        // And backwards from the first one.
        assertTrue(session.jumpToMatch(forward = false))
        assertEquals(occurrences[1].startOffset, myFixture.editor.caretModel.offset)
    }

    fun testNavigationDoesNothingWhileNoResultIsAvailable() {
        val session = startSession(FocusMode.STATIC, DeferredRunner())

        assertFalse(session.jumpToMatch(forward = true))
        assertEquals(0, myFixture.editor.caretModel.offset)
    }

    fun testLeavesFilesAboveTheConfiguredLimitAlone() {
        settings.loadState(TunnelVisionSettings.State(maxFileLines = 100))
        configureLongFile(lines = 100)

        val session = startSession(FocusMode.STATIC, state = TunnelVisionSettings.State(maxFileLines = 100))

        val result = session.state.result
        assertTrue(
            "the large file must be refused with a reason, but got: $result",
            result is FocusResult.Unavailable && result.reason.contains("above the configured limit"),
        )
        // Nothing was searched for, which is the point of the guard.
        assertEquals(emptyList<FocusRequest>(), computation.requests)
    }

    fun testComputesFilesUpToTheConfiguredLimit() {
        configureLongFile(lines = 10)

        startSession(FocusMode.STATIC, state = TunnelVisionSettings.State(maxFileLines = 100))

        assertEquals(1, computation.requests.size)
    }

    /** @return the first two occurrences of `count` in the fixture, for navigation tests. */
    private fun occurrenceRanges(): List<TextRange> {
        val text = myFixture.editor.document.text
        val occurrences = mutableListOf<TextRange>()
        var from = 0
        while (occurrences.size < 2) {
            val index = text.indexOf("count", from)
            if (index < 0) break
            occurrences += TextRange(index, index + "count".length)
            from = index + 1
        }
        return occurrences
    }

    private fun configureLongFile(lines: Int) {
        myFixture.configureByText(
            "Long.java",
            buildString {
                append("class Long {\n")
                repeat(lines) { append("    int count$it = 0;\n") }
                append("}\n")
            },
        )
    }

    private fun startSession(
        mode: FocusMode,
        runner: RefreshRunner = InlineRunner(),
        state: TunnelVisionSettings.State = TunnelVisionSettings.State(mode = mode),
        notifier: FocusNotifier = FocusNotifier { },
    ): FocusSession {
        settings.loadState(state)
        return FocusSession(
            state = EditorFocusState(myFixture.editor),
            settingsProvider = { settings },
            computationProvider = { computation },
            debouncer = debouncer,
            runner = runner,
            notifier = notifier,
        ).also { it.start() }
    }

    private fun moveCaretTo(offset: Int) {
        myFixture.editor.caretModel.moveToOffset(offset)
    }
}

/** Records the requests it is asked to compute, so tests can assert anchoring. */
private class RecordingComputation : FocusComputation {

    val requests = mutableListOf<FocusRequest>()

    /** Occurrences the fake source reports, which is what navigation follows. */
    var occurrences: List<TextRange> = listOf(TextRange(1, 2))

    /** When set, the fake source refuses to answer with this reason. */
    var unavailableReason: String? = null

    /** Whether that reason is one the user has to act on, as the real sources mark them. */
    var unavailableReported = true

    override fun compute(editor: Editor, request: FocusRequest): FocusResult {
        requests += request
        unavailableReason?.let { return FocusResult.Unavailable(it, report = unavailableReported) }
        return FocusResult.Matched("count", FocusAreas.of(symbol = occurrences))
    }
}

/** Records what the session reported to the user. */
private class RecordingNotifier : FocusNotifier {

    val reasons = mutableListOf<String>()

    override fun report(reason: String) {
        reasons += reason
    }
}

/** Runs the pass on the calling thread, which keeps the session tests deterministic. */
private class InlineRunner : RefreshRunner {
    override fun run(
        state: EditorFocusState,
        compute: () -> FocusResult,
        onResult: (FocusResult) -> Unit,
    ) {
        onResult(compute())
    }
}

/** Holds results back so tests can deliver them out of order or after a disposal. */
private class DeferredRunner : RefreshRunner {

    private val pending = ArrayDeque<(FocusResult) -> Unit>()

    override fun run(
        state: EditorFocusState,
        compute: () -> FocusResult,
        onResult: (FocusResult) -> Unit,
    ) {
        pending += onResult
    }

    fun deliver(result: FocusResult) {
        pending.removeFirst()(FocusResult.Batch(listOf(result)))
    }
}

/** Stands in for the EDT alarm so debouncing can be asserted without waiting. */
private class ManualDebouncer : Debouncer {

    private var pending: Runnable? = null

    var delayMillis: Int? = null
        private set

    var scheduleCount = 0
        private set

    var cancelCount = 0
        private set

    override fun schedule(delayMillis: Int, task: Runnable) {
        this.delayMillis = delayMillis
        scheduleCount++
        pending = task
    }

    override fun cancel() {
        if (pending != null) cancelCount++
        pending = null
    }

    fun fire() {
        val task = checkNotNull(pending) { "no refresh is pending" }
        pending = null
        task.run()
    }
}
