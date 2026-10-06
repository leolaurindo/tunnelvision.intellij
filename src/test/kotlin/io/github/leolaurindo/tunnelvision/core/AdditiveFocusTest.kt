package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.leolaurindo.tunnelvision.settings.FocusMode
import io.github.leolaurindo.tunnelvision.settings.HighlightArea
import io.github.leolaurindo.tunnelvision.settings.MatchSource
import io.github.leolaurindo.tunnelvision.settings.TunnelVisionSettings
import io.github.leolaurindo.tunnelvision.source.SourceComputation

class AdditiveFocusTest : BasePlatformTestCase() {

    private lateinit var session: FocusSession
    private lateinit var settings: TunnelVisionSettings
    private val debounce = PendingDebouncer()

    override fun setUp() {
        super.setUp()
        settings = TunnelVisionSettings()
        settings.state.source = MatchSource.WORD
        settings.state.areas = mutableListOf(HighlightArea.LINE, HighlightArea.SYMBOL)
        myFixture.configureByText("sample.txt", "alpha beta\nalpha beta\noutside\n")
    }

    override fun tearDown() {
        try {
            if (::session.isInitialized) session.dispose()
        } finally {
            super.tearDown()
        }
    }

    fun testAddRetainsBothSymbolsAndDimsOnlyOutsideTheirCombinedContext() {
        start()
        moveTo("beta")
        session.activate()

        assertEquals(listOf("alpha", "beta"), symbols())
        assertEquals(listOf(0, 6, 11, 17), occurrences())
        val dimmed = myFixture.editor.markupModel.allHighlighters.filter {
            it.getTextAttributes(myFixture.editor.colorsScheme)?.foregroundColor != null
        }
        assertFalse(dimmed.isEmpty())
        assertTrue(dimmed.none { it.startOffset < 10 && it.endOffset > 0 })
        assertTrue(dimmed.none { it.startOffset < 21 && it.endOffset > 11 })
        assertTrue(dimmed.any { it.startOffset <= 22 && it.endOffset > 22 })

        session.remove()
        assertEquals(listOf("alpha"), symbols())
        assertEquals(listOf(0, 11), occurrences())
    }

    fun testDuplicateAtAnotherOccurrenceDoesNotAddATrack() {
        start()
        myFixture.editor.caretModel.moveToOffset(11)
        session.activate()

        assertEquals(listOf("alpha"), symbols())
    }

    fun testInvalidAddAndReplacePreserveTracksButValidReplaceRetargets() {
        start()
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        session.activate()
        session.activate(replace = true)

        assertEquals(listOf("alpha"), symbols())
        assertEquals(listOf(0, 11), occurrences())

        moveTo("beta")
        session.activate(replace = true)
        assertEquals(listOf("beta"), symbols())
        assertEquals(listOf(6, 17), occurrences())
    }

    fun testDynamicFocusRetargetsWithoutMovingPinsAndKeepsLastTargetOnWhitespace() {
        start(FocusMode.DYNAMIC)
        session.activate(pin = true)
        assertEquals(listOf(FocusMode.DYNAMIC, FocusMode.STATIC), session.state.tracks.map { it.mode })

        moveTo("beta")
        debounce.fire()
        assertEquals(listOf("beta", "alpha"), symbols())
        assertEquals(listOf(6, 0), session.state.tracks.map { it.anchorOffset })

        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        debounce.fire()
        assertEquals(listOf("beta", "alpha"), symbols())
        assertEquals(listOf(0, 6, 11, 17), occurrences())

        moveTo("outside")
        session.activate()
        assertEquals(listOf("alpha", "outside"), symbols())
        assertEquals(1, session.state.tracks.count { it.mode == FocusMode.DYNAMIC })
    }

    fun testAllAnchorsFollowEditsAndDefaultsDoNotChangeExistingModes() {
        start()
        moveTo("beta")
        session.activate()
        settings.state.mode = FocusMode.DYNAMIC
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(0, "header\n")
        }
        debounce.fire()

        assertEquals(listOf("alpha", "beta"), symbols())
        assertEquals(listOf(7, 13), session.state.tracks.map { it.anchorOffset })
        assertEquals(listOf(7, 13, 18, 24), occurrences())
        assertTrue(session.state.tracks.all { it.mode == FocusMode.STATIC })
    }

    fun testNavigationVisitsDifferentSymbolsOnTheSameLineAndWraps() {
        start()
        moveTo("beta")
        session.activate()
        myFixture.editor.caretModel.moveToOffset(0)

        for (offset in listOf(6, 11, 17, 0)) {
            assertTrue(session.jumpToMatch(true))
            assertEquals(offset, myFixture.editor.caretModel.offset)
        }
        assertTrue(session.jumpToMatch(false))
        assertEquals(17, myFixture.editor.caretModel.offset)
    }

    fun testRemoveAwayFromSymbolsPopsNewestAndRemovesItsHighlight() {
        start()
        moveTo("beta")
        session.activate()
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        session.remove()

        assertEquals(listOf("alpha"), symbols())
        session.remove()
        assertTrue(session.state.tracks.isEmpty())
        assertTrue(myFixture.editor.markupModel.allHighlighters.isEmpty())
    }

    fun testDifferentPsiDeclarationsWithSameSpellingRemainSeparateWithinOneFunction() {
        myFixture.configureByText(
            "Sample.java",
            "class Sample { void run() { { int count = 0; count++; } { int count = 1; count++; } } }",
        )
        settings.state.source = MatchSource.PSI
        moveTo("count")
        start()
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.lastIndexOf("count"))
        session.activate()
        assertEquals(listOf("count", "count"), symbols())
        assertEquals(4, occurrences().size)

        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("count++"))
        session.activate()
        assertEquals(2, session.state.tracks.size)
    }

    fun testSamePsiDeclarationInDifferentFunctionsKeepsSeparateScopes() {
        myFixture.configureByText(
            "Sample.java",
            "class Sample { int count; void first() { count++; } void second() { count++; } }",
        )
        settings.state.source = MatchSource.PSI
        moveTo("count++")
        start()
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.lastIndexOf("count"))
        session.activate()

        assertEquals(listOf("count", "count"), symbols())
        assertEquals(2, occurrences().size)
    }

    fun testRemovedPendingTrackCannotReturnThroughALateActivationResult() {
        val runner = QueuedRunner()
        start(runner = runner)
        runner.deliver()
        moveTo("beta")
        session.activate()
        session.remove()
        runner.deliver()

        assertEquals(listOf("alpha"), symbols())
        assertEquals(listOf(0, 11), occurrences())
        runner.deliver()
        assertEquals(listOf("alpha"), symbols())
    }

    fun testRemoveAwayFromOccurrencesCancelsNewestPendingAddNotAnExistingPin() {
        val runner = QueuedRunner()
        start(runner = runner)
        runner.deliver()
        moveTo("beta")
        session.activate()
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        session.remove()
        runner.deliver()
        runner.deliver()

        assertEquals(listOf("alpha"), symbols())
        assertEquals(listOf(0, 11), occurrences())
    }

    fun testRapidAddsAreRetainedWhenAnEarlierRefreshIsStillPending() {
        val runner = QueuedRunner()
        start(runner = runner)
        runner.deliver()
        moveTo("beta")
        session.activate()
        moveTo("outside")
        session.activate()
        runner.deliver()
        assertEquals(listOf("alpha"), symbols())
        runner.deliver()
        assertEquals(listOf("alpha", "beta", "outside"), symbols())
        assertEquals(listOf(0, 6, 11, 17, 22), occurrences())
    }

    fun testLateDynamicResultCannotReplaceANewerTarget() {
        val runner = QueuedRunner()
        start(FocusMode.DYNAMIC, runner)
        runner.deliver()
        moveTo("beta")
        debounce.fire()
        moveTo("outside")
        debounce.fire()
        runner.deliver()
        assertEquals(listOf("alpha"), symbols())
        runner.deliver()
        assertEquals(listOf("outside"), symbols())
    }

    private fun start(mode: FocusMode = FocusMode.STATIC, runner: RefreshRunner = RefreshRunner { _, compute, accept ->
        accept(compute())
    }) {
        settings.state.mode = mode
        session = FocusSession(
            EditorFocusState(myFixture.editor), { settings }, { SourceComputation() },
            debouncer = debounce, runner = runner, notifier = { },
        )
        session.start()
    }

    private fun moveTo(text: String) {
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf(text))
    }

    private fun symbols(): List<String> = session.state.tracks.mapNotNull { (it.result as? FocusResult.Matched)?.symbol }

    private fun occurrences(): List<Int> = (session.state.result as FocusResult.Matched)
        .areas.ranges(HighlightArea.SYMBOL).map(TextRange::getStartOffset)
}

private class PendingDebouncer : Debouncer {
    private var pending: Runnable? = null

    override fun schedule(delayMillis: Int, task: Runnable) {
        pending = task
    }

    override fun cancel() {
        pending = null
    }

    fun fire() {
        val task = checkNotNull(pending)
        pending = null
        task.run()
    }
}

private class QueuedRunner : RefreshRunner {
    private val callbacks = ArrayDeque<() -> Unit>()

    override fun run(state: EditorFocusState, compute: () -> FocusResult, onResult: (FocusResult) -> Unit) {
        val result = compute()
        callbacks += { onResult(result) }
    }

    fun deliver() {
        callbacks.removeFirst()()
    }
}
