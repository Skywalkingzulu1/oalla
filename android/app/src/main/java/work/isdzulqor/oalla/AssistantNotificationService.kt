package work.isdzulqor.oalla

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AssistantNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "assistant_gen_channel"
        const val NOTIF_ID = 2001
        const val ACTION_UPDATE_TEXT = "UPDATE_TEXT"
        const val EXTRA_TEXT = "text"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_INDEX = "chat_index"
        const val EXTRA_CHAT_IS_COMPLETE = "chat_is_complete"
        var suppressUntil: Long = 0

        fun updateNotification(context: Context, text: String, chatId: String, messageIndex: Int, isComplete: Boolean = false) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            // 🚫 Suppress updates if within 5 seconds of last tap
            if (System.currentTimeMillis() < suppressUntil) {
                Log.d("NotificationService", "Notification suppressed due to recent tap.")
                return
            }

            try {
                val intent = Intent(context, AssistantNotificationService::class.java).apply {
                    action = ACTION_UPDATE_TEXT
                    putExtra(EXTRA_TEXT, text)
                    putExtra(EXTRA_CHAT_ID, chatId)
                    putExtra(EXTRA_CHAT_INDEX, messageIndex)
                    putExtra(EXTRA_CHAT_IS_COMPLETE, isComplete)
                }

                context.startService(intent)
            } catch (e: Exception) {
                Log.e("NotificationService", "Failed to start AssistantNotificationService", e)
            }
        }

        fun stopNotification(context: Context) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIF_ID) // ✅ Remove the notification

                context.stopService(Intent(context, AssistantNotificationService::class.java))
            } catch (e: Exception) {
                Log.e("NotificationService", "Failed to stop AssistantNotificationService", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_UPDATE_TEXT) {
            val rawText = intent.getStringExtra(EXTRA_TEXT) ?: ""
            val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
            val chatIndex = intent.getIntExtra(EXTRA_CHAT_INDEX, 0)
            val isComplete : Boolean = intent.getBooleanExtra(EXTRA_CHAT_IS_COMPLETE, false)

            val displayText = if (rawText.length > 80) {
                rawText.take(80).trimEnd() + "… more"
            } else {
                rawText
            }

            showNotification(displayText, chatId, chatIndex, isComplete)
        }

        // Optional: stop automatically after notification is shown
        stopSelf()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showNotification(text: String, chatId: String, chatIndex: Int, isComplete: Boolean = false) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_chat", true)
            putExtra("chat_id", chatId)
            putExtra("chat_index", chatIndex)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isComplete) "Assistant Response Ready" else "Assistant is Generating…"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(!isComplete) // Ongoing only if still generating
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Assistant Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}