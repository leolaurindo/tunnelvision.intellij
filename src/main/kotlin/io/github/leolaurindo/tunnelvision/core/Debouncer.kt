package io.github.leolaurindo.tunnelvision.core

import com.intellij.openapi.Disposable
import com.intellij.util.Alarm

/** Delays refreshes so typing and caret movement do not recompute on every event. */
interface Debouncer {

    /** Schedules [task] to run once, [delayMillis] from now. Call [cancel] to drop a pending task. */
    fun schedule(delayMillis: Int, task: Runnable)

    /** Drops the pending task, if any. */
    fun cancel()
}

/** Runs the task on the EDT; disposing the focus state lifetime cancels it too. */
class AlarmDebouncer(lifetime: Disposable) : Debouncer {

    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, lifetime)

    override fun schedule(delayMillis: Int, task: Runnable) {
        alarm.addRequest(task, delayMillis)
    }

    override fun cancel() {
        alarm.cancelAllRequests()
    }
}
