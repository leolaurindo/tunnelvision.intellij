package io.github.leolaurindo.tunnelvision.source

import io.github.leolaurindo.tunnelvision.core.FocusComputation
import io.github.leolaurindo.tunnelvision.core.FocusResult
import io.github.leolaurindo.tunnelvision.settings.MatchSource

class SourceComputationTest : SourceTestCase() {

    fun testRoutesTheRefreshToTheConfiguredSource() {
        val calls = mutableListOf<String>()
        val computation = SourceComputation(
            psi = FocusComputation { _, _ -> calls += "psi"; FocusResult.Unavailable("psi") },
            word = FocusComputation { _, _ -> calls += "word"; FocusResult.Unavailable("word") },
        )
        myFixture.configureByText("Sample.java", "class Sample {}")

        compute(computation, source = MatchSource.WORD)
        compute(computation, source = MatchSource.PSI)

        assertEquals(listOf("word", "psi"), calls)
    }
}
