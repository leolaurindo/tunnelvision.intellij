package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TunnelVisionCoreTest : BasePlatformTestCase() {

    fun testToggleActivatesAndDeactivatesTheCurrentEditor() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val core = TunnelVisionCore()
        val editor = myFixture.editor

        assertFalse(core.isActive(editor))

        assertTrue(core.toggle(editor))
        assertTrue(core.isActive(editor))
        assertNotNull(core.stateOf(editor))

        assertFalse(core.toggle(editor))
        assertFalse(core.isActive(editor))
        assertNull(core.stateOf(editor))
    }

    fun testVersionsIncreaseSoStaleResultsCanBeRejected() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val state = EditorFocusState(myFixture.editor)

        assertEquals(0, state.version)
        assertEquals(1, state.nextVersion())
        assertEquals(2, state.nextVersion())
    }

    fun testActivationKeepsOneStatePerEditor() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val core = TunnelVisionCore()
        val editor = myFixture.editor

        val first = core.activate(editor)

        assertSame(first, core.activate(editor))
        assertFalse(first.isDisposed)
    }

    fun testDeactivationDisposesTheEditorState() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val core = TunnelVisionCore()
        val editor = myFixture.editor
        val state = core.activate(editor)

        core.deactivate(editor)

        assertTrue(state.isDisposed)
        assertFalse(core.isActive(editor))
    }

    fun testRefreshingAllRecomputesEveryActiveEditor() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val core = TunnelVisionCore()
        val editor = myFixture.editor
        val version = core.activate(editor).version

        core.refreshAll()

        assertTrue(core.stateOf(editor)!!.version > version)
    }

    fun testEachEditorOfTheSameDocumentKeepsItsOwnState() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        // An editor split shows the same document in two editors, each with its own focus.
        val split = EditorFactory.getInstance().createEditor(myFixture.editor.document, project)
        try {
            val core = TunnelVisionCore()
            val state = core.activate(myFixture.editor)
            val splitState = core.activate(split)

            assertNotSame(state, splitState)
            assertTrue(core.isActive(split))

            core.deactivate(myFixture.editor)

            assertFalse(core.isActive(myFixture.editor))
            assertTrue(core.isActive(split))
            assertFalse(splitState.isDisposed)
        } finally {
            EditorFactory.getInstance().releaseEditor(split)
        }
    }

    fun testReleaseOfAnEditorDropsItsState() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val core = TunnelVisionCore.getInstance()
        val editor = myFixture.editor
        core.activate(editor)

        EditorReleaseListener().editorReleased(EditorFactoryEvent(EditorFactory.getInstance(), editor))

        assertFalse(core.isActive(editor))
    }
}
