package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.util.concurrency.AppExecutorUtil

/** Runs one matching pass away from the UI thread and delivers its result back on the UI thread. */
fun interface RefreshRunner {
    fun run(
        state: EditorFocusState,
        compute: () -> FocusResult,
        onResult: (FocusResult) -> Unit,
    )
}

/**
 * Production runner: a cancellable background read action.
 *
 * A newer request coalesced on the same state cancels this one, and closing the editor disposes
 * the state, so neither the read action nor its UI callback can outlive the focused editor.
 */
class ReadActionRefreshRunner : RefreshRunner {

    override fun run(
        state: EditorFocusState,
        compute: () -> FocusResult,
        onResult: (FocusResult) -> Unit,
    ) {
        val action = ReadAction.nonBlocking<FocusResult> { compute() }
            .coalesceBy(state)
            .expireWith(state.lifetime)

        state.editor.project?.let {
            // Committing documents keeps PSI consistent with the text the request was made against,
            // and waiting for the indexes keeps the search out of dumb mode.
            action.withDocumentsCommitted(it)
            action.inSmartMode(it)
        }

        action
            .finishOnUiThread(ModalityState.any(), onResult)
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}
