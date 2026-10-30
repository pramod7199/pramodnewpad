package com.pramod.loopmidi.admin;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class AdminMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(String token) {
        AdminNotifications.subscribeToNewRequests(this);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        AdminNotifications.createNotificationChannel(this);

        RemoteMessage.Notification remoteNotification = message.getNotification();
        String title = remoteNotification != null && remoteNotification.getTitle() != null
                ? remoteNotification.getTitle() : "New access request";
        String body = remoteNotification != null && remoteNotification.getBody() != null
                ? remoteNotification.getBody() : "Open the Admin Panel to review the request.";

        Intent intent = new Intent(this, AdminLoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, intent, pendingIntentFlags);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                this, AdminNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_admin_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            int notificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
            manager.notify(notificationId, notification.build());
        }
    }
}
