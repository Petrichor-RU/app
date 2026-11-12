package nl.petrichor.app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.datetime.TimeZone
import nl.petrichor.app.ui.journal.JournalPendingAction
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Android implementation (plan section 2.2): two unique 24h [PeriodicWorkRequest]s whose
 * initial delay is the next wall-clock occurrence of the reminder hour (see [ReminderTimes]).
 * The worker posts a notification whose tap opens MainActivity with a `journal_action` extra.
 */
actual object NotificationScheduler {
    const val CHANNEL_ID = "journal_reminders"
    const val EXTRA_ACTION = "journal_action"
    const val MAIN_ACTIVITY_CLASS = "nl.petrichor.app.MainActivity"

    private const val MORNING_WORK = "journal_morning"
    private const val EVENING_WORK = "journal_evening"
    internal const val EXTRA_TITLE = "journal_title"
    internal const val EXTRA_TEXT = "journal_text"
    internal const val EXTRA_REQUEST_CODE = "journal_request_code"
    private const val REQUEST_ADD_OUTFIT = 1
    private const val REQUEST_SURVEY = 2

    private var appContext: Context? = null

    /** Creates the notification channel and schedules both reminders. Call once from MainActivity. */
    fun init(context: Context) {
        appContext = context.applicationContext
        createChannel(context)
        scheduleDailyReminders()
    }

    actual fun scheduleDailyReminders() {
        val context = appContext ?: return
        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        scheduleWork(
            context,
            ReminderSpec(
                name = MORNING_WORK,
                nextAt = ReminderTimes.nextOccurrence(now, ReminderTimes.MORNING_HOUR, zone),
                title = "Log today's outfit",
                text = "Take a selfie or pick your pieces.",
                action = JournalPendingAction.ADD_OUTFIT.name,
                requestCode = REQUEST_ADD_OUTFIT,
            ),
        )
        scheduleWork(
            context,
            ReminderSpec(
                name = EVENING_WORK,
                nextAt = ReminderTimes.nextOccurrence(now, ReminderTimes.EVENING_HOUR, zone),
                title = "How did it feel today?",
                text = "Fill in your evening survey.",
                action = JournalPendingAction.SURVEY.name,
                requestCode = REQUEST_SURVEY,
            ),
        )
    }

    private fun scheduleWork(
        context: Context,
        spec: ReminderSpec,
    ) {
        val delayMs = (spec.nextAt.toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0)
        val request =
            PeriodicWorkRequestBuilder<JournalReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        EXTRA_ACTION to spec.action,
                        EXTRA_TITLE to spec.title,
                        EXTRA_TEXT to spec.text,
                        EXTRA_REQUEST_CODE to spec.requestCode,
                    ),
                ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(spec.name, ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL_ID, "Journal reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Daily reminders to log your outfit and fill in the evening survey"
                }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }
}

/** One reminder schedule entry. */
private class ReminderSpec(
    val name: String,
    val nextAt: Instant,
    val title: String,
    val text: String,
    val action: String,
    val requestCode: Int,
)

/** Maps the `journal_action` intent extra to a [JournalPendingAction]; call from MainActivity. */
fun journalActionFromIntent(intent: Intent?): JournalPendingAction? =
    when (intent?.getStringExtra(NotificationScheduler.EXTRA_ACTION)) {
        JournalPendingAction.ADD_OUTFIT.name -> JournalPendingAction.ADD_OUTFIT
        JournalPendingAction.SURVEY.name -> JournalPendingAction.SURVEY
        else -> null
    }

/** Posts the reminder notification when the periodic work runs. */
class JournalReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val action = inputData.getString(NotificationScheduler.EXTRA_ACTION)
        val title = inputData.getString(NotificationScheduler.EXTRA_TITLE)
        if (action != null && title != null) {
            postNotification(
                action,
                title,
                inputData.getString(NotificationScheduler.EXTRA_TEXT).orEmpty(),
                inputData.getInt(NotificationScheduler.EXTRA_REQUEST_CODE, 0),
            )
        }
        return Result.success()
    }

    private fun postNotification(
        action: String,
        title: String,
        text: String,
        requestCode: Int,
    ) {
        val intent =
            Intent()
                .setClassName(applicationContext, NotificationScheduler.MAIN_ACTIVITY_CLASS)
                .apply {
                    putExtra(NotificationScheduler.EXTRA_ACTION, action)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(applicationContext, NotificationScheduler.CHANNEL_ID)
                .setSmallIcon(applicationContext.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        NotificationManagerCompat.from(applicationContext).notify(requestCode, notification)
    }
}
