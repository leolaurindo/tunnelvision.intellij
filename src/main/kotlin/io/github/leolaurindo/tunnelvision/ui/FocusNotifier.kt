package io.github.leolaurindo.tunnelvision.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications

/**
 * Tells the user why focus could not be computed.
 *
 * A source that cannot answer stays configured as it is; this is how the reason reaches the user
 * instead of the editor simply staying undecorated.
 */
fun interface FocusNotifier {
    fun report(reason: String)
}

/** Balloon notification; the group is declared in the plugin descriptor. */
class BalloonFocusNotifier : FocusNotifier {

    override fun report(reason: String) {
        Notifications.Bus.notify(Notification(GROUP_ID, "TunnelVision", reason, NotificationType.WARNING))
    }

    companion object {
        const val GROUP_ID = "TunnelVision"
    }
}
