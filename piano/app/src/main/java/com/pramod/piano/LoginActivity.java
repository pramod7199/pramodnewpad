package com.pramod.piano;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public final class LoginActivity extends Activity {
    private static final int REQUEST_SIGN_IN = 9001;
    private static final String DATABASE_URL =
            "https://pramod-octapad-loop-default-rtdb.asia-southeast1.firebasedatabase.app";

    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;
    private DatabaseReference accessRef;
    private ValueEventListener accessListener;
    private TextView status;
    private Button signInButton;
    private Button signOutButton;
    private boolean pendingRequestSent;
    private boolean launchedMain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        auth = FirebaseAuth.getInstance();
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, options);
        signInButton.setOnClickListener(view -> startGoogleSignIn());
        signOutButton.setOnClickListener(view -> signOut());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showSignedOut();
        } else {
            observePianoAccess(user);
        }
    }

    @Override
    protected void onDestroy() {
        clearAccessListener();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(8, 10, 13));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(28), dp(24), dp(24));
        card.setBackgroundColor(Color.rgb(25, 30, 38));

        TextView title = new TextView(this);
        title.setText("PRAMOD PIANO");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title, matchWidth(dp(40)));

        TextView subtitle = new TextView(this);
        subtitle.setText("Sign in to verify your Piano access");
        subtitle.setTextColor(Color.rgb(184, 198, 217));
        subtitle.setTextSize(14f);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWidth(dp(46));
        subtitleParams.topMargin = dp(8);
        card.addView(subtitle, subtitleParams);

        status = new TextView(this);
        status.setTextColor(Color.rgb(255, 178, 154));
        status.setTextSize(14f);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, dp(12));
        card.addView(status, matchWidth(LinearLayout.LayoutParams.WRAP_CONTENT));

        signInButton = new Button(this);
        signInButton.setText("Continue with Google");
        signInButton.setAllCaps(false);
        card.addView(signInButton, matchWidth(dp(48)));

        signOutButton = new Button(this);
        signOutButton.setText("Sign out");
        signOutButton.setAllCaps(false);
        signOutButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams signOutParams = matchWidth(dp(48));
        signOutParams.topMargin = dp(8);
        card.addView(signOutButton, signOutParams);

        root.addView(card, matchWidth(LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void startGoogleSignIn() {
        status.setText("Opening Google sign-in…");
        signInButton.setEnabled(false);
        startActivityForResult(googleSignInClient.getSignInIntent(), REQUEST_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SIGN_IN) return;

        try {
            GoogleSignInAccount account = GoogleSignIn
                    .getSignedInAccountFromIntent(data)
                    .getResult(ApiException.class);
            if (account.getIdToken() == null) {
                showError("Google sign-in did not return an ID token. Try again.");
                return;
            }
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
                if (!task.isSuccessful()) {
                    Exception error = task.getException();
                    showError(error == null ? "Firebase sign-in failed." : error.getMessage());
                    return;
                }
                FirebaseUser user = auth.getCurrentUser();
                if (user == null) {
                    showError("Google sign-in succeeded, but no Firebase user was returned.");
                    return;
                }
                observePianoAccess(user);
            });
        } catch (ApiException error) {
            showError("Google sign-in failed (code " + error.getStatusCode() + ").");
        }
    }

    private void observePianoAccess(FirebaseUser user) {
        clearAccessListener();
        pendingRequestSent = false;
        signInButton.setEnabled(false);
        signOutButton.setVisibility(View.VISIBLE);

        accessRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("authorizedUsers")
                .child(user.getUid());
        accessListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    FirebaseDatabase.getInstance(DATABASE_URL)
                            .getReference("pendingRequests")
                            .child(user.getUid())
                            .removeValue();
                    Boolean allowed = snapshot.child("allowedApps")
                            .child("piano").getValue(Boolean.class);
                    if (Boolean.TRUE.equals(allowed)) {
                        enterPiano();
                    } else {
                        status.setText("Piano access is not enabled for this account. "
                                + "Please ask the admin to grant Piano access.");
                        signInButton.setEnabled(false);
                    }
                    return;
                }

                status.setText("Waiting for admin approval. Your request is being sent…");
                signInButton.setEnabled(false);
                if (!pendingRequestSent) {
                    pendingRequestSent = true;
                    sendPendingRequest(user);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                showError("Could not verify Piano access: " + error.getMessage());
            }
        };
        accessRef.addValueEventListener(accessListener);
    }

    private void sendPendingRequest(FirebaseUser user) {
        Map<String, Object> request = new HashMap<>();
        request.put("email", user.getEmail() == null ? "" : user.getEmail());
        request.put("displayName", user.getDisplayName() == null ? "" : user.getDisplayName());
        request.put("timestamp", System.currentTimeMillis());
        request.put("requestedApp", "piano");
        FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("pendingRequests")
                .child(user.getUid())
                .setValue(request)
                .addOnFailureListener(error ->
                        showError("Could not send the approval request: " + error.getMessage()));
    }

    private void enterPiano() {
        if (launchedMain || isFinishing()) return;
        launchedMain = true;
        status.setText("Piano access confirmed.");
        clearAccessListener();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void signOut() {
        clearAccessListener();
        auth.signOut();
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                showError(error == null ? "Could not complete Google sign-out."
                        : "Could not complete Google sign-out: " + error.getMessage());
                return;
            }
            showSignedOut();
        });
    }

    private void showSignedOut() {
        pendingRequestSent = false;
        signInButton.setEnabled(true);
        signOutButton.setVisibility(View.GONE);
        status.setText("Sign in with the Google account registered by the admin.");
    }

    private void showError(String message) {
        status.setText(message == null || message.trim().isEmpty()
                ? "An unexpected sign-in error occurred."
                : message);
        signInButton.setEnabled(auth.getCurrentUser() == null);
        signOutButton.setVisibility(auth.getCurrentUser() == null ? View.GONE : View.VISIBLE);
    }

    private void clearAccessListener() {
        if (accessListener != null && accessRef != null) {
            accessRef.removeEventListener(accessListener);
        }
        accessListener = null;
        accessRef = null;
    }

    private LinearLayout.LayoutParams matchWidth(int height) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
