package com.pramod.loopmidi.admin;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

public final class AdminNotifications {

    static final String TOPIC = "admin-new-requests";
    static final String CHANNEL_ID = "admin_requests";
    static final String CHANNEL_NAME = "New access requests";
    private static final String TAG = "AdminNotifications";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 701;

    private AdminNotifications() {}

    public static void registerForNewRequests(Activity activity) {
        createNotificationChannel(activity);

        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }

        AdminFirebaseApp.get(activity);
        FirebaseMessaging.getInstance()
                .subscribeToTopic(TOPIC)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Could not subscribe to new-request notifications",
                                task.getException());
                    }
                });
    }

    static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Alerts when a user requests app access.");
        manager.createNotificationChannel(channel);
    }

    static void subscribeToNewRequests(Context context) {
        AdminFirebaseApp.get(context);
        FirebaseMessaging.getInstance()
                .subscribeToTopic(TOPIC)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Could not restore new-request notification subscription",
                                task.getException());
                    }
                });
    }
}
