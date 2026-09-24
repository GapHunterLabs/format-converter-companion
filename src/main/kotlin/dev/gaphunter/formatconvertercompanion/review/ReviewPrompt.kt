package dev.gaphunter.formatconvertercompanion.review

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader

/**
 * Asks the user to rate the plugin on Marketplace, once, after they've
 * used a real number of successful conversions -- never on install,
 * never on a timer. Every call to [recordHit] here already corresponds
 * to one real conversion (the document was actually rewritten, never
 * the error branch). Counts invocations directly instead of tracking a
 * set of seen keys.
 *
 * Threshold is deliberately lower than the detector-based plugins'
 * (5, not 10) -- an explicit action invocation is a much stronger
 * signal of real value than a passive finding the plugin noticed on
 * its own.
 *
 * Same "earn the ask" principle as other well-regarded plugins; never
 * re-asks once the user has either rated or dismissed it.
 *
 * Persisted via [PropertiesComponent] at the application level (not
 * per-project) -- how many times this plugin has been used isn't tied
 * to any one project, and neither is whether the user already
 * answered.
 */
object ReviewPrompt {

    /** How many successful conversions before the prompt shows once. */
    private const val HITS_BEFORE_PROMPT = 5

    private const val KEY_HIT_COUNT = "dev.gaphunter.formatconvertercompanion.review.hitCount"
    private const val KEY_ANSWERED = "dev.gaphunter.formatconvertercompanion.review.answered"

    private const val NOTIFICATION_GROUP_ID = "Format Converter Companion"

    // Marketplace listing (real ID resolved after first publish).
    private const val MARKETPLACE_URL = "https://plugins.jetbrains.com/plugin/33342-format-converter-companion/reviews"

    /**
     * Call this once per real, successful conversion. Safe to call on
     * any thread.
     */
    fun recordHit(project: Project?) {
        val properties = PropertiesComponent.getInstance()
        if (properties.getBoolean(KEY_ANSWERED)) return

        val count = properties.getInt(KEY_HIT_COUNT, 0) + 1
        properties.setValue(KEY_HIT_COUNT, count, 0)

        if (count == HITS_BEFORE_PROMPT) {
            showPrompt(project)
        }
    }

    private fun showPrompt(project: Project?) {
        val properties = PropertiesComponent.getInstance()

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                "Format Converter Companion",
                "You've used this $HITS_BEFORE_PROMPT times -- if it's saved you time, a rating on the Marketplace helps other developers find it.",
                NotificationType.INFORMATION,
            )
        notification.setIcon(IconLoader.getIcon("/META-INF/pluginIcon.svg", ReviewPrompt::class.java))

        notification.addAction(NotificationAction.createSimpleExpiring("Rate on Marketplace") {
            properties.setValue(KEY_ANSWERED, true)
            BrowserUtil.browse(MARKETPLACE_URL)
        })
        notification.addAction(NotificationAction.createSimpleExpiring("Don't ask again") {
            properties.setValue(KEY_ANSWERED, true)
        })

        notification.notify(project)
    }
}
