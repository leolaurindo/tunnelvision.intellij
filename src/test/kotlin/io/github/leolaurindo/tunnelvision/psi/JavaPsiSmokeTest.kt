package io.github.leolaurindo.tunnelvision.psi

import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Guards the Java PSI test fixture itself: the resolver stage builds on these APIs.
 */
class JavaPsiSmokeTest : BasePlatformTestCase() {

    fun testResolvesLocalVariableReferenceUnderCaret() {
        myFixture.configureByText(
            "Sample.java",
            """
            class Sample {
                void run() {
                    int count = 0;
                    System.out.println(co<caret>unt);
                }
            }
            """.trimIndent(),
        )

        val resolved = myFixture.getReferenceAtCaretPositionWithAssertion().resolve()

        assertInstanceOf(resolved, PsiNamedElement::class.java)
        assertEquals("count", (resolved as PsiNamedElement).name)
    }
}
