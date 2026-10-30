package com.pramod.loopmidi.admin;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Manually configured connection to the SAME Firebase project used by the
 * main "Pramod Octapad Loops" app (project: pramod-octapad-loop).
 *
 * This app deliberately does NOT use google-services.json / the Google
 * Services Gradle plugin. That mechanism registers an OAuth client tied to
 * this app's own package name + signing certificate, which would require a
 * second app registration in the Firebase console. Since this admin app
 * uses Firebase Auth (email/password), the Realtime Database, and Firebase
 * Cloud Messaging -- never Google Sign-In. Building FirebaseOptions by hand
 * keeps the admin app independent of the Google Sign-In OAuth client setup.
 */
public class AdminFirebaseApp {

    private static final String APP_NAME = FirebaseApp.DEFAULT_APP_NAME;
    private static final String PROJECT_ID = "pramod-octapad-loop";
    private static final String APPLICATION_ID = "1:1070428492610:android:463803a2e3c8dff1b0eedb";
    private static final String API_KEY = "AIzaSyAM5iLKu7EBcodDUgBiGadmIAsMv82yxhc";
    public static final String DB_URL =
            "https://pramod-octapad-loop-default-rtdb.asia-southeast1.firebasedatabase.app";

    private static FirebaseApp app;

    public static synchronized FirebaseApp get(Context context) {
        if (app != null) return app;
        try {
            app = FirebaseApp.getInstance(APP_NAME);
        } catch (IllegalStateException notYetCreated) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setProjectId(PROJECT_ID)
                    .setApplicationId(APPLICATION_ID)
                    .setGcmSenderId("1070428492610")
                    .setApiKey(API_KEY)
                    .setDatabaseUrl(DB_URL)
                    .build();
            app = FirebaseApp.initializeApp(context.getApplicationContext(), options, APP_NAME);
        }
        return app;
    }

    public static FirebaseAuth auth(Context context) {
        return FirebaseAuth.getInstance(get(context));
    }

    public static FirebaseDatabase database(Context context) {
        return FirebaseDatabase.getInstance(get(context), DB_URL);
    }
}
