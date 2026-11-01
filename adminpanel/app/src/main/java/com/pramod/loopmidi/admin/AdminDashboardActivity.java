package com.pramod.loopmidi.admin;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private DatabaseReference pendingRef;
    private DatabaseReference usersRef;
    private DatabaseReference deactivatedRef;
    private DatabaseReference profilesRef;

    private LinearLayout pendingContainer;
    private LinearLayout activeContainer;
    private LinearLayout deactivatedContainer;
    private TextView txtPendingEmpty;
    private TextView txtActiveEmpty;
    private TextView txtDeactivatedEmpty;
    private TextView txtSearchEmpty;
    private EditText editManualUid;
    private EditText editSearch;
    private Button btnRefresh;
    private Button btnFilterPending;
    private Button btnFilterActive;
    private Button btnFilterDeactivated;
    private Button btnExpiredBell;
    private TextView txtExpiredBellDot;
    private ValueEventListener pendingListener;
    private ValueEventListener activeListener;
    private ValueEventListener deactivatedListener;
    private ValueEventListener profilesListener;
    private int refreshPendingReads;
    private int refreshFailureCount;
    private StringBuilder refreshErrors;
    private final Handler expiryHandler = new Handler(Looper.getMainLooper());
    private final Runnable expiryCheck = new Runnable() {
        @Override
        public void run() {
            updateExpiredBell();
            expiryHandler.postDelayed(this, 60_000);
        }
    };

    // Checkboxes for the manual-activate section
    private CheckBox cbManualFull;
    private CheckBox cbManualLoops;
    private CheckBox cbManualDrums;
    private CheckBox cbManualPro;

    private enum Filter { PENDING, ACTIVE, DEACTIVATED }
    private Filter currentFilter = Filter.PENDING;

    private final List<UserEntry> allUsers = new ArrayList<>();
    private final List<UserEntry> deactivatedUsers = new ArrayList<>();
    private final List<UserEntry> registeredUsers = new ArrayList<>();
    private final List<PendingEntry> pendingRequests = new ArrayList<>();

    private static class UserEntry {
        String uid, email, displayName, deviceToken;
        boolean hasFull, hasLoops, hasDrums, hasPro;
        boolean isDeactivated;
        boolean needsAccessSelection;
        String accessType;
        long activatedAt;
        long expiresAt;

        boolean hasAnyAppAccess() {
            return hasFull || hasLoops || hasDrums || hasPro;
        }

        boolean isTrialExpired(long now) {
            return expiresAt > 0
                    && (accessType == null || "trial".equalsIgnoreCase(accessType))
                    && expiresAt <= now;
        }
    }

    private static class PendingEntry {
        String uid, email, displayName;
        long timestamp;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = AdminFirebaseApp.auth(this);
        pendingRef = AdminFirebaseApp.database(this).getReference("pendingRequests");
        usersRef   = AdminFirebaseApp.database(this).getReference("authorizedUsers");
        deactivatedRef = AdminFirebaseApp.database(this).getReference("deactivatedUsers");
        profilesRef = AdminFirebaseApp.database(this).getReference("users");
        buildUi();
        AdminNotifications.registerForNewRequests(this);
        listenPending();
        listenActive();
        listenDeactivated();
        listenProfiles();
    }

    @Override
    protected void onDestroy() {
        if (pendingListener != null) pendingRef.removeEventListener(pendingListener);
        if (activeListener != null) usersRef.removeEventListener(activeListener);
        if (deactivatedListener != null) deactivatedRef.removeEventListener(deactivatedListener);
        if (profilesListener != null) profilesRef.removeEventListener(profilesListener);
        expiryHandler.removeCallbacks(expiryCheck);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        expiryHandler.removeCallbacks(expiryCheck);
        expiryCheck.run();
    }

    @Override
    protected void onPause() {
        expiryHandler.removeCallbacks(expiryCheck);
        super.onPause();
    }

    // ── UI construction (no XML) ──────────────────────────────────────────────

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff111111);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, dp(32), pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Admin Panel");
        title.setTextColor(0xff00afff);
        title.setTextSize(20);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout bellContainer = new FrameLayout(this);
        btnExpiredBell = new Button(this);
        btnExpiredBell.setText("🔔");
        btnExpiredBell.setContentDescription("Expired trial users");
        btnExpiredBell.setOnClickListener(v -> showExpiredUsers());
        bellContainer.addView(btnExpiredBell, new FrameLayout.LayoutParams(
                dp(64), LinearLayout.LayoutParams.WRAP_CONTENT));
        txtExpiredBellDot = new TextView(this);
        txtExpiredBellDot.setText("●");
        txtExpiredBellDot.setTextColor(0xffff2222);
        txtExpiredBellDot.setTextSize(16);
        txtExpiredBellDot.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                dp(18), dp(18), Gravity.TOP | Gravity.END);
        bellContainer.addView(txtExpiredBellDot, dotParams);
        header.addView(bellContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btnRefresh = new Button(this);
        btnRefresh.setText("Refresh");
        btnRefresh.setOnClickListener(v -> refreshLists());
        header.addView(btnRefresh);
        Button btnLogout = new Button(this);
        btnLogout.setText("Logout");
        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(this, AdminLoginActivity.class));
            finish();
        });
        header.addView(btnLogout);
        root.addView(header, matchWidth(0));

        root.addView(sectionHeader("Search User"), matchWidth(dp(16)));
        editSearch = new EditText(this);
        editSearch.setHint("UID, email, ya name se search karein...");
        editSearch.setTextColor(0xffffffff);
        editSearch.setHintTextColor(0xff888888);
        editSearch.setSingleLine(true);
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applySearchAndFilter();
            }
        });
        root.addView(editSearch, matchWidth(dp(4)));

        txtSearchEmpty = new TextView(this);
        txtSearchEmpty.setText("User not found");
        txtSearchEmpty.setTextColor(0xffff4444);
        txtSearchEmpty.setTextSize(13);
        txtSearchEmpty.setVisibility(View.GONE);
        root.addView(txtSearchEmpty, matchWidth(dp(2)));

        root.addView(sectionHeader("Users"), matchWidth(dp(16)));
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        btnFilterPending = makeFilterButton("Pending", true);
        btnFilterActive = makeFilterButton("Active", false);
        btnFilterDeactivated = makeFilterButton("Deactivated", false);
        btnFilterPending.setOnClickListener(v -> selectFilter(Filter.PENDING));
        btnFilterActive.setOnClickListener(v -> selectFilter(Filter.ACTIVE));
        btnFilterDeactivated.setOnClickListener(v -> selectFilter(Filter.DEACTIVATED));
        filterRow.addView(btnFilterPending);
        filterRow.addView(btnFilterActive);
        filterRow.addView(btnFilterDeactivated);
        root.addView(filterRow, matchWidth(dp(8)));

        pendingContainer = new LinearLayout(this);
        pendingContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(pendingContainer, matchWidth(dp(4)));
        txtPendingEmpty = emptyText("Koi pending request nahi hai.");
        root.addView(txtPendingEmpty, matchWidth(dp(4)));

        activeContainer = new LinearLayout(this);
        activeContainer.setOrientation(LinearLayout.VERTICAL);
        activeContainer.setVisibility(View.GONE);
        root.addView(activeContainer, matchWidth(dp(4)));
        txtActiveEmpty = emptyText("Koi user nahi hai.");
        txtActiveEmpty.setVisibility(View.GONE);
        root.addView(txtActiveEmpty, matchWidth(dp(4)));

        deactivatedContainer = new LinearLayout(this);
        deactivatedContainer.setOrientation(LinearLayout.VERTICAL);
        deactivatedContainer.setVisibility(View.GONE);
        root.addView(deactivatedContainer, matchWidth(dp(4)));
        txtDeactivatedEmpty = emptyText("Koi deactivated user nahi hai.");
        txtDeactivatedEmpty.setVisibility(View.GONE);
        root.addView(txtDeactivatedEmpty, matchWidth(dp(4)));

        // Manual activate section
        root.addView(sectionHeader("Manually Activate by UID (fallback)"), matchWidth(dp(24)));
        buildManualSection(root);

        setContentView(scroll);
    }

    private Button makeFilterButton(String label, boolean selected) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(11);
        btn.setTextColor(0xffffffff);
        btn.setBackgroundColor(selected ? 0xff00afff : 0xff333333);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.rightMargin = dp(4);
        btn.setLayoutParams(p);
        return btn;
    }

    private void selectFilter(Filter filter) {
        currentFilter = filter;
        updateFilterButtons();
        applySearchAndFilter();
    }

    private void updateFilterButtons() {
        btnFilterPending.setBackgroundColor(currentFilter == Filter.PENDING ? 0xff00afff : 0xff333333);
        btnFilterActive.setBackgroundColor(currentFilter == Filter.ACTIVE ? 0xff00afff : 0xff333333);
        btnFilterDeactivated.setBackgroundColor(currentFilter == Filter.DEACTIVATED ? 0xff00afff : 0xff333333);
        btnFilterPending.setText("Pending (" + visiblePendingCount() + ")");
        btnFilterActive.setText("Active (" + activeUserCount() + ")");
        btnFilterDeactivated.setText("Deactivated (" + visibleDeactivatedCount() + ")");
    }

    private void buildManualSection(LinearLayout root) {
        // UID input
        LinearLayout uidRow = new LinearLayout(this);
        uidRow.setOrientation(LinearLayout.HORIZONTAL);
        editManualUid = new EditText(this);
        editManualUid.setHint("Firebase User UID paste karein");
        editManualUid.setTextColor(0xffffffff);
        editManualUid.setHintTextColor(0xff888888);
        uidRow.addView(editManualUid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(uidRow, matchWidth(dp(4)));

        // APK checkboxes (all checked by default)
        TextView apkLabel = new TextView(this);
        apkLabel.setText("Kis APK ka access dena hai:");
        apkLabel.setTextColor(0xffaaaaaa);
        apkLabel.setTextSize(11);
        apkLabel.setPadding(0, dp(4), 0, dp(2));
        root.addView(apkLabel, matchWidth(0));

        LinearLayout cbRow = new LinearLayout(this);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbManualFull  = makeCheckBox("Full",  true);
        cbManualLoops = makeCheckBox("Loops", true);
        cbManualDrums = makeCheckBox("Drums", true);
        cbManualPro = makeCheckBox("Pro", false);
        cbRow.addView(cbManualFull);
        cbRow.addView(cbManualLoops);
        cbRow.addView(cbManualDrums);
        cbRow.addView(cbManualPro);
        root.addView(cbRow, matchWidth(dp(2)));

        Button btnManualActivate = new Button(this);
        btnManualActivate.setText("Activate");
        btnManualActivate.setOnClickListener(v -> manualActivate());
        root.addView(btnManualActivate, matchWidth(dp(4)));
    }

    // ── Pending list ──────────────────────────────────────────────────────────

    private void listenPending() {
        pendingListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                updatePending(snapshot);
            }
            @Override public void onCancelled(DatabaseError e) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Pending list error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        pendingRef.addValueEventListener(pendingListener);
    }

    private void updatePending(DataSnapshot snapshot) {
        pendingRequests.clear();
        for (DataSnapshot child : snapshot.getChildren()) {
            PendingEntry entry = new PendingEntry();
            entry.uid = child.getKey();
            entry.email = readText(child.child("email"));
            entry.displayName = readText(child.child("displayName"));
            entry.timestamp = readTimestamp(child.child("timestamp"));
            pendingRequests.add(entry);
        }
        pendingRequests.sort((left, right) -> Long.compare(right.timestamp, left.timestamp));
        applySearchAndFilter();
    }

    private View buildPendingRow(String uid, String email, String displayName, String subLine) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(0xff1c1c1c);
        int pad = dp(10);
        row.setPadding(pad, pad, pad, pad);
        row.setLayoutParams(matchWidth(dp(6)));

        addUserInfoViews(row, uid, email, displayName, subLine);

        // APK selection label
        TextView apkLabel = new TextView(this);
        apkLabel.setText("Kis APK ka access dena hai:");
        apkLabel.setTextColor(0xffaaaaaa);
        apkLabel.setTextSize(11);
        apkLabel.setPadding(0, dp(6), 0, dp(2));
        row.addView(apkLabel);

        // Checkboxes — all ticked by default
        LinearLayout cbRow = new LinearLayout(this);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        CheckBox cbFull  = makeCheckBox("Full",  true);
        CheckBox cbLoops = makeCheckBox("Loops", true);
        CheckBox cbDrums = makeCheckBox("Drums", true);
        CheckBox cbPro = makeCheckBox("Pro", false);
        cbRow.addView(cbFull);
        cbRow.addView(cbLoops);
        cbRow.addView(cbDrums);
        cbRow.addView(cbPro);
        row.addView(cbRow);

        // Action buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(6), 0, 0);

        Button btnActivate = new Button(this);
        btnActivate.setText("Activate");
        btnActivate.setOnClickListener(v ->
                showActivationTypeDialog(uid, email, displayName,
                        cbFull.isChecked(), cbLoops.isChecked(), cbDrums.isChecked(),
                        cbPro.isChecked()));
        btnRow.addView(btnActivate, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnReject = new Button(this);
        btnReject.setText("Reject");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rp.leftMargin = dp(6);
        btnReject.setOnClickListener(v -> pendingRef.child(uid).removeValue());
        btnRow.addView(btnReject, rp);

        row.addView(btnRow);
        return row;
    }

    // ── Active list ───────────────────────────────────────────────────────────

    private void listenActive() {
        activeListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                updateActive(snapshot);
            }
            @Override public void onCancelled(DatabaseError e) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Active list error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        usersRef.addValueEventListener(activeListener);
    }

    private void updateActive(DataSnapshot snapshot) {
        allUsers.clear();
        for (DataSnapshot child : snapshot.getChildren()) {
            UserEntry entry = parseUserEntry(child, false);
            if (entry != null) allUsers.add(entry);
        }
        android.util.Log.i("AdminDashboard",
                "Loaded " + allUsers.size() + " records from authorizedUsers");
        applySearchAndFilter();
    }

    // ── Deactivated list listener ─────────────────────────────────────────────

    private void listenDeactivated() {
        deactivatedListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                updateDeactivated(snapshot);
            }
            @Override public void onCancelled(DatabaseError e) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Deactivated list error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        deactivatedRef.addValueEventListener(deactivatedListener);
    }

    private void updateDeactivated(DataSnapshot snapshot) {
        deactivatedUsers.clear();
        for (DataSnapshot child : snapshot.getChildren()) {
            UserEntry entry = parseUserEntry(child, true);
            if (entry != null) deactivatedUsers.add(entry);
        }
        android.util.Log.i("AdminDashboard",
                "Loaded " + deactivatedUsers.size() + " records from deactivatedUsers");
        applySearchAndFilter();
    }

    private void listenProfiles() {
        profilesListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                updateProfiles(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Registered user list error: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        };
        profilesRef.addValueEventListener(profilesListener);
    }

    private interface SnapshotUpdate {
        void apply(DataSnapshot snapshot);
    }

    private void refreshLists() {
        if (refreshPendingReads > 0) return;
        refreshPendingReads = 4;
        refreshFailureCount = 0;
        refreshErrors = new StringBuilder();
        btnRefresh.setEnabled(false);
        btnRefresh.setText("Refreshing...");

        refreshReference(pendingRef, "Pending", this::updatePending);
        refreshReference(usersRef, "Active", this::updateActive);
        refreshReference(deactivatedRef, "Deactivated", this::updateDeactivated);
        refreshReference(profilesRef, "Registered users", this::updateProfiles);
    }

    private void refreshReference(DatabaseReference reference, String listName,
                                  SnapshotUpdate update) {
        reference.get().addOnCompleteListener(this, task -> {
            if (isFinishing() || isDestroyed()) return;

            if (task.isSuccessful() && task.getResult() != null) {
                update.apply(task.getResult());
            } else {
                Exception error = task.getException();
                if (error != null) {
                    android.util.Log.e("AdminDashboard", listName + " refresh failed", error);
                }
                recordRefreshFailure(listName);
            }

            completeRefreshRead();
        });
    }

    private void completeRefreshRead() {
        refreshPendingReads--;
        if (refreshPendingReads != 0 || isFinishing() || isDestroyed()) return;

        btnRefresh.setEnabled(true);
        btnRefresh.setText("Refresh");
        String message = refreshFailureCount == 0
                ? "Lists refreshed."
                : "Could not refresh: " + refreshErrors;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void recordRefreshFailure(String listName) {
        refreshFailureCount++;
        if (refreshErrors.length() > 0) refreshErrors.append(", ");
        refreshErrors.append(listName);
    }

    private void updateProfiles(DataSnapshot snapshot) {
        registeredUsers.clear();
        for (DataSnapshot child : snapshot.getChildren()) {
            UserEntry entry = new UserEntry();
            entry.uid = child.getKey();
            DataSnapshot profile = child.child("profile");
            entry.email = readText(profile.child("email"));
            entry.displayName = readText(profile.child("displayName"));
            entry.isDeactivated = true;
            entry.needsAccessSelection = true;
            registeredUsers.add(entry);
        }
        applySearchAndFilter();
    }

    /** Parse a DataSnapshot into a UserEntry. */
    private UserEntry parseUserEntry(DataSnapshot child, boolean isDeactivated) {
        UserEntry entry = new UserEntry();
        entry.uid = child.getKey();
        entry.email = readText(child.child("email"));
        entry.displayName = readText(child.child("displayName"));
        entry.deviceToken = readText(child.child("deviceToken"));
        entry.isDeactivated = isDeactivated;
        entry.accessType = readText(child.child("accessType"));

        DataSnapshot appsSnap = child.child("allowedApps");
        // Older authorized records predate per-app access and are valid for all apps.
        entry.hasFull = readAppAccess(appsSnap, "full");
        entry.hasLoops = readAppAccess(appsSnap, "loops");
        entry.hasDrums = readAppAccess(appsSnap, "drums");
        entry.hasPro = appsSnap.exists() && readAppAccess(appsSnap, "pro");

        entry.activatedAt = readTimestamp(child.child("activatedAt"));
        entry.expiresAt = readTimestamp(child.child("expiresAt"));
        return entry;
    }

    private long readTimestamp(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private String readText(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        return value instanceof String ? (String) value : null;
    }

    private boolean readAppAccess(DataSnapshot appsSnapshot, String appName) {
        if (!appsSnapshot.exists()) return true;

        Object value = appsSnapshot.child(appName).getValue();
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) {
            String normalized = ((String) value).trim().toLowerCase(Locale.ROOT);
            return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
        }
        return false;
    }

    private void applySearchAndFilter() {
        String query = "";
        if (editSearch != null && editSearch.getText() != null) {
            query = editSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        }
        String q = query;

        pendingContainer.setVisibility(currentFilter == Filter.PENDING ? View.VISIBLE : View.GONE);
        txtPendingEmpty.setVisibility(currentFilter == Filter.PENDING ? View.VISIBLE : View.GONE);
        activeContainer.setVisibility(currentFilter == Filter.ACTIVE ? View.VISIBLE : View.GONE);
        txtActiveEmpty.setVisibility(currentFilter == Filter.ACTIVE ? View.VISIBLE : View.GONE);
        deactivatedContainer.setVisibility(
                currentFilter == Filter.DEACTIVATED ? View.VISIBLE : View.GONE);
        txtDeactivatedEmpty.setVisibility(
                currentFilter == Filter.DEACTIVATED ? View.VISIBLE : View.GONE);

        boolean any = false;
        pendingContainer.removeAllViews();
        activeContainer.removeAllViews();
        deactivatedContainer.removeAllViews();

        if (currentFilter == Filter.PENDING) {
            for (PendingEntry entry : pendingRequests) {
                if (hasAuthorizedRecord(entry.uid) || !matchesSearch(
                        entry.uid, entry.email, entry.displayName, q)) continue;
                String subLine = entry.timestamp > 0
                        ? "Request: " + new java.util.Date(entry.timestamp) : null;
                pendingContainer.addView(buildPendingRow(
                        entry.uid, entry.email, entry.displayName, subLine));
                any = true;
            }
            txtPendingEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
        } else if (currentFilter == Filter.ACTIVE) {
            for (UserEntry entry : allUsers) {
                if (!entry.hasAnyAppAccess() || !matchesSearch(
                        entry.uid, entry.email, entry.displayName, q)) continue;
                activeContainer.addView(buildUserRow(entry));
                any = true;
            }
            txtActiveEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
        } else {
            for (UserEntry entry : allUsers) {
                if (entry.hasAnyAppAccess() || hasPendingRequest(entry.uid)
                        || !matchesSearch(entry.uid, entry.email, entry.displayName, q)) continue;
                deactivatedContainer.addView(buildUserRow(entry));
                any = true;
            }
            for (UserEntry entry : deactivatedUsers) {
                if (hasAuthorizedRecord(entry.uid) || hasPendingRequest(entry.uid)
                        || !matchesSearch(entry.uid, entry.email, entry.displayName, q)) continue;
                deactivatedContainer.addView(buildUserRow(entry));
                any = true;
            }
            for (UserEntry entry : registeredUsers) {
                if (hasAuthorizedRecord(entry.uid) || hasDeactivatedRecord(entry.uid)
                        || hasPendingRequest(entry.uid)
                        || !matchesSearch(entry.uid, entry.email, entry.displayName, q)) continue;
                deactivatedContainer.addView(buildUserRow(entry));
                any = true;
            }
            txtDeactivatedEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
        }

        updateFilterButtons();
        if (txtSearchEmpty != null) {
            txtSearchEmpty.setVisibility(!q.isEmpty() && !any ? View.VISIBLE : View.GONE);
        }
        updateExpiredBell();
    }

    private boolean matchesSearch(String uid, String email, String displayName, String query) {
        return query.isEmpty()
                || (uid != null && uid.toLowerCase(Locale.ROOT).contains(query))
                || (email != null && email.toLowerCase(Locale.ROOT).contains(query))
                || (displayName != null && displayName.toLowerCase(Locale.ROOT).contains(query));
    }

    private boolean hasAuthorizedRecord(String uid) {
        for (UserEntry entry : allUsers) {
            if (uid != null && uid.equals(entry.uid)) return true;
        }
        return false;
    }

    private boolean hasPendingRequest(String uid) {
        for (PendingEntry entry : pendingRequests) {
            if (uid != null && uid.equals(entry.uid)) return true;
        }
        return false;
    }

    private boolean hasDeactivatedRecord(String uid) {
        for (UserEntry entry : deactivatedUsers) {
            if (uid != null && uid.equals(entry.uid)) return true;
        }
        return false;
    }

    private int activeUserCount() {
        int count = 0;
        for (UserEntry entry : allUsers) {
            if (entry.hasAnyAppAccess()) count++;
        }
        return count;
    }

    private int visiblePendingCount() {
        int count = 0;
        for (PendingEntry entry : pendingRequests) {
            if (!hasAuthorizedRecord(entry.uid)) count++;
        }
        return count;
    }

    private int visibleDeactivatedCount() {
        int count = 0;
        for (UserEntry entry : allUsers) {
            if (!entry.hasAnyAppAccess() && !hasPendingRequest(entry.uid)) count++;
        }
        for (UserEntry entry : deactivatedUsers) {
            if (!hasAuthorizedRecord(entry.uid) && !hasPendingRequest(entry.uid)) count++;
        }
        for (UserEntry entry : registeredUsers) {
            if (!hasAuthorizedRecord(entry.uid) && !hasDeactivatedRecord(entry.uid)
                    && !hasPendingRequest(entry.uid)) count++;
        }
        return count;
    }

    private List<UserEntry> getExpiredTrialUsers() {
        long now = System.currentTimeMillis();
        List<UserEntry> expired = new ArrayList<>();
        for (UserEntry entry : allUsers) {
            if (entry.hasAnyAppAccess() && entry.isTrialExpired(now)) expired.add(entry);
        }
        expired.sort(Comparator.comparingLong(user -> user.expiresAt));
        return expired;
    }

    private void updateExpiredBell() {
        if (btnExpiredBell == null || txtExpiredBellDot == null) return;
        int expiredCount = getExpiredTrialUsers().size();
        txtExpiredBellDot.setVisibility(expiredCount > 0 ? View.VISIBLE : View.GONE);
        btnExpiredBell.setContentDescription(
                expiredCount > 0
                        ? "Expired trial users: " + expiredCount
                        : "No expired trial users");
    }

    private void showExpiredUsers() {
        List<UserEntry> expired = getExpiredTrialUsers();
        if (expired.isEmpty()) {
            Toast.makeText(this, "Koi expired trial user nahi hai.", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        String[] labels = new String[expired.size()];
        for (int i = 0; i < expired.size(); i++) {
            UserEntry entry = expired.get(i);
            String name = !TextUtils.isEmpty(entry.displayName)
                    ? entry.displayName
                    : !TextUtils.isEmpty(entry.email) ? entry.email : entry.uid;
            labels[i] = name + " — expired " + dateFormat.format(new Date(entry.expiresAt));
        }

        new AlertDialog.Builder(this)
                .setTitle("Expired trials (" + expired.size() + ")")
                .setItems(labels, (dialog, index) -> confirmDeactivate(expired.get(index)))
                .setNegativeButton("Close", null)
                .show();
    }

    private View buildUserRow(UserEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        boolean noAppAccess = !entry.isDeactivated && !entry.hasAnyAppAccess();
        row.setBackgroundColor(entry.isDeactivated || noAppAccess
                ? 0xff2a1515 : 0xff1c1c1c);
        int pad = dp(10);
        row.setPadding(pad, pad, pad, pad);
        row.setLayoutParams(matchWidth(dp(6)));

        // Status badge
        TextView txtStatus = new TextView(this);
        txtStatus.setText(entry.needsAccessSelection ? "⚠ REGISTERED — NO ACTIVE ACCESS"
                : entry.isDeactivated ? "❌ DEACTIVATED"
                : noAppAccess ? "⛔ NO APP ACCESS" : "✅ ACTIVE");
        txtStatus.setTextColor(entry.isDeactivated || noAppAccess
                ? 0xffff4444 : 0xff44ff44);
        txtStatus.setTextSize(11);
        txtStatus.setPadding(0, 0, 0, dp(2));
        row.addView(txtStatus);

        // User info
        addUserInfoViews(row, entry.uid, entry.email, entry.displayName,
                "APK: " + (entry.hasFull ? "✅Full " : "❌Full ")
                        + (entry.hasLoops ? "✅Loops " : "❌Loops ")
                        + (entry.hasDrums ? "✅Drums " : "❌Drums ")
                        + (entry.hasPro ? "✅Pro" : "❌Pro")
                        + (entry.deviceToken != null ? "  |  🔒 Device locked" : "  |  🔓 Unlocked"));

        // Action buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(6), 0, 0);

        if (entry.isDeactivated) {
            Button btnReactivate = new Button(this);
            btnReactivate.setText(entry.needsAccessSelection ? "Grant Access" : "Reactivate");
            btnReactivate.setOnClickListener(v -> {
                if (entry.needsAccessSelection) {
                    showActivateRegisteredUserDialog(entry);
                } else {
                    reactivateUser(entry);
                }
            });
            btnRow.addView(btnReactivate, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else if (noAppAccess) {
            Button btnGrantAccess = new Button(this);
            btnGrantAccess.setText("Grant Access");
            btnGrantAccess.setOnClickListener(v ->
                    showEditAppsDialog(entry.uid, entry.email, entry.displayName,
                            entry.hasFull, entry.hasLoops, entry.hasDrums, entry.hasPro));
            btnRow.addView(btnGrantAccess, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            // Active user → Deactivate + Edit Apps + Unlock buttons
            Button btnDeactivate = new Button(this);
            btnDeactivate.setText("Deactivate");
            btnDeactivate.setOnClickListener(v -> confirmDeactivate(entry));
            btnRow.addView(btnDeactivate, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button btnEdit = new Button(this);
            btnEdit.setText("Edit Apps");
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            ep.leftMargin = dp(6);
            btnEdit.setOnClickListener(v ->
                    showEditAppsDialog(entry.uid, entry.email, entry.displayName,
                            entry.hasFull, entry.hasLoops, entry.hasDrums, entry.hasPro));
            btnRow.addView(btnEdit, ep);

            if (!TextUtils.isEmpty(entry.deviceToken)) {
                Button btnUnlock = new Button(this);
                btnUnlock.setText("Unlock");
                LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                up.leftMargin = dp(6);
                btnUnlock.setOnClickListener(v ->
                        usersRef.child(entry.uid).child("deviceToken").removeValue());
                btnRow.addView(btnUnlock, up);
            }
        }

        row.addView(btnRow);
        return row;
    }

    private void showActivateRegisteredUserDialog(UserEntry entry) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);

        CheckBox cbFull = makeCheckBox("Full", true);
        CheckBox cbLoops = makeCheckBox("Loops", true);
        CheckBox cbDrums = makeCheckBox("Drums", true);
        CheckBox cbPro = makeCheckBox("Pro", false);
        layout.addView(cbFull);
        layout.addView(cbLoops);
        layout.addView(cbDrums);
        layout.addView(cbPro);

        new AlertDialog.Builder(this)
                .setTitle("Grant app access")
                .setMessage("Purana access record nahi mila. Apps select karein:")
                .setView(layout)
                .setPositiveButton("Activate", (dialog, which) ->
                        activateUser(entry.uid, entry.email, entry.displayName,
                                cbFull.isChecked(), cbLoops.isChecked(), cbDrums.isChecked(),
                                cbPro.isChecked(), false, 0))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Firebase actions ──────────────────────────────────────────────────────

    private void showActivationTypeDialog(String uid, String email, String displayName,
                                          boolean full, boolean loops, boolean drums,
                                          boolean pro) {
        String[] choices = {"Permanent", "Trial"};
        new AlertDialog.Builder(this)
                .setTitle("Access duration")
                .setItems(choices, (dialog, index) -> {
                    if (index == 0) {
                        activateUser(uid, email, displayName,
                                full, loops, drums, pro, false, 0);
                    } else {
                        showTrialExpiryPicker(uid, email, displayName,
                                full, loops, drums, pro);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTrialExpiryPicker(String uid, String email, String displayName,
                                       boolean full, boolean loops, boolean drums,
                                       boolean pro) {
        Calendar initial = Calendar.getInstance();
        initial.add(Calendar.DAY_OF_YEAR, 1);
        DatePickerDialog datePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar expiry = Calendar.getInstance();
                    expiry.set(year, month, dayOfMonth);
                    new TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> {
                                expiry.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                expiry.set(Calendar.MINUTE, minute);
                                expiry.set(Calendar.SECOND, 0);
                                expiry.set(Calendar.MILLISECOND, 0);
                                long expiresAt = expiry.getTimeInMillis();
                                if (expiresAt <= System.currentTimeMillis()) {
                                    Toast.makeText(this,
                                            "Expiry date and time must be in the future.",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }
                                activateUser(uid, email, displayName,
                                        full, loops, drums, pro, true, expiresAt);
                            },
                            initial.get(Calendar.HOUR_OF_DAY),
                            initial.get(Calendar.MINUTE),
                            DateFormat.is24HourFormat(this))
                            .show();
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
        datePicker.setTitle("Trial expiry date");
        datePicker.show();
    }

    private void activateUser(String uid, String email, String displayName,
                              boolean full, boolean loops, boolean drums, boolean pro,
                              boolean trial, long expiresAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("email",       email != null ? email : "");
        data.put("displayName", displayName != null ? displayName : "");
        data.put("activatedAt", System.currentTimeMillis());
        data.put("accessType", trial ? "trial" : "permanent");
        if (trial) data.put("expiresAt", expiresAt);

        Map<String, Object> appsMap = new HashMap<>();
        appsMap.put("full",  full);
        appsMap.put("loops", loops);
        appsMap.put("drums", drums);
        appsMap.put("pro", pro);
        data.put("allowedApps", appsMap);

        usersRef.child(uid).setValue(data).addOnCompleteListener(this, saveTask -> {
            if (!saveTask.isSuccessful()) {
                Exception error = saveTask.getException();
                android.util.Log.e("AdminDashboard", "Could not activate user " + uid, error);
                Toast.makeText(this, "User activation failed. Please try again.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            pendingRef.child(uid).removeValue().addOnCompleteListener(this, removeTask -> {
                if (!removeTask.isSuccessful()) {
                    Exception error = removeTask.getException();
                    android.util.Log.e("AdminDashboard",
                            "Could not remove activated request " + uid, error);
                    Toast.makeText(this,
                            "User activated, but the pending request could not be removed.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                String type = trial ? "Trial activated until "
                        + new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                .format(new Date(expiresAt))
                        : "Permanently activated";
                Toast.makeText(this,
                        type + ": " + (!TextUtils.isEmpty(email) ? email : uid),
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void saveAllowedApps(String uid, String email,
                                 boolean full, boolean loops, boolean drums, boolean pro) {
        Map<String, Object> appsMap = new HashMap<>();
        appsMap.put("full",  full);
        appsMap.put("loops", loops);
        appsMap.put("drums", drums);
        appsMap.put("pro", pro);
        usersRef.child(uid).child("allowedApps").setValue(appsMap);
        Toast.makeText(this, "Access updated: " + (email != null ? email : uid), Toast.LENGTH_SHORT).show();
    }

    /** Deactivate: move from authorizedUsers to deactivatedUsers (instead of deleting). */
    private void confirmDeactivate(UserEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Deactivate karein?")
                .setMessage("Yeh user turant logout ho jayega. Aap ise baad me Reactivate kar sakte hain.")
                .setPositiveButton("Deactivate", (dialog, which) ->
                        moveUserRecord(entry, usersRef, "authorizedUsers",
                                deactivatedRef, "deactivatedUsers", false))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Reactivate: move from deactivatedUsers back to authorizedUsers. */
    private void reactivateUser(UserEntry entry) {
        moveUserRecord(entry, deactivatedRef, "deactivatedUsers",
                usersRef, "authorizedUsers", true);
    }

    private void moveUserRecord(UserEntry entry, DatabaseReference source,
                                String sourcePath, DatabaseReference destination,
                                String destinationPath, boolean activating) {
        source.child(entry.uid).get().addOnCompleteListener(this, task -> {
            if (!task.isSuccessful() || task.getResult() == null
                    || !task.getResult().exists()) {
                Exception error = task.getException();
                if (error != null) {
                    android.util.Log.e("AdminDashboard",
                            "Could not read " + sourcePath + " record for " + entry.uid, error);
                }
                Toast.makeText(this, "User data could not be loaded. Nothing was changed.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            Object rawRecord = task.getResult().getValue();
            if (!(rawRecord instanceof Map)) {
                android.util.Log.e("AdminDashboard",
                        "Invalid " + sourcePath + " record for " + entry.uid);
                Toast.makeText(this, "User data is invalid. Nothing was changed.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            Map<String, Object> record = new HashMap<>();
            for (Map.Entry<?, ?> field : ((Map<?, ?>) rawRecord).entrySet()) {
                if (field.getKey() instanceof String) {
                    record.put((String) field.getKey(), field.getValue());
                }
            }

            if (activating) {
                record.put("activatedAt", System.currentTimeMillis());
                record.remove("deactivatedAt");
            } else {
                record.put("deactivatedAt", System.currentTimeMillis());
            }

            DatabaseReference destinationUser = destination.child(entry.uid);
            destinationUser.setValue(record).addOnCompleteListener(this, saveTask -> {
                if (!saveTask.isSuccessful()) {
                    logMoveFailure("Could not save " + destinationPath, entry.uid,
                            saveTask.getException());
                    Toast.makeText(this,
                            "Could not save the user in " + destinationPath
                                    + ". The current record was kept.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                source.child(entry.uid).removeValue().addOnCompleteListener(this, removeTask -> {
                    if (!removeTask.isSuccessful()) {
                        logMoveFailure("Could not remove " + sourcePath, entry.uid,
                                removeTask.getException());
                        Toast.makeText(this,
                                "The destination record was saved, but the old record could not "
                                        + "be removed. Check Firebase permissions and refresh.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    String status = activating ? "Reactivated: " : "Deactivated: ";
                    String identity = !TextUtils.isEmpty(entry.email) ? entry.email : entry.uid;
                    Toast.makeText(this, status + identity, Toast.LENGTH_SHORT).show();
                });
            });
        });
    }

    private void logMoveFailure(String action, String uid, Exception error) {
        android.util.Log.e("AdminDashboard", action + " record for " + uid, error);
    }

    private void manualActivate() {
        String uid = editManualUid.getText().toString().trim();
        if (TextUtils.isEmpty(uid)) {
            Toast.makeText(this, "UID daalein.", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("activatedAt", System.currentTimeMillis());
        data.put("accessType", "permanent");
        data.put("note", "manually added");

        Map<String, Object> appsMap = new HashMap<>();
        appsMap.put("full",  cbManualFull.isChecked());
        appsMap.put("loops", cbManualLoops.isChecked());
        appsMap.put("drums", cbManualDrums.isChecked());
        appsMap.put("pro", cbManualPro.isChecked());
        data.put("allowedApps", appsMap);

        usersRef.child(uid).setValue(data);
        editManualUid.setText("");
        Toast.makeText(this, "Activated by UID.", Toast.LENGTH_SHORT).show();
    }

    private void showEditAppsDialog(String uid, String email, String displayName,
                                    boolean curFull, boolean curLoops, boolean curDrums,
                                    boolean curPro) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);

        CheckBox cbFull  = makeCheckBox("Full  (com.pramod.loopmidi)",        curFull);
        CheckBox cbLoops = makeCheckBox("Loops (com.pramod.loopmidi.loops)",  curLoops);
        CheckBox cbDrums = makeCheckBox("Drums (com.pramod.loopmidi.drums)",  curDrums);
        CheckBox cbPro = makeCheckBox("Pro (com.pramod.loopmidi.pro)", curPro);
        layout.addView(cbFull);
        layout.addView(cbLoops);
        layout.addView(cbDrums);
        layout.addView(cbPro);

        new AlertDialog.Builder(this)
                .setTitle("APK Access — " + (!TextUtils.isEmpty(email) ? email : uid))
                .setView(layout)
                .setPositiveButton("Save", (d, w) ->
                        saveAllowedApps(uid, email,
                                cbFull.isChecked(), cbLoops.isChecked(), cbDrums.isChecked(),
                                cbPro.isChecked()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── View helpers ──────────────────────────────────────────────────────────

    private void addUserInfoViews(LinearLayout row, String uid, String email,
                                  String displayName, String subLine) {
        TextView txtName = new TextView(this);
        txtName.setText(!TextUtils.isEmpty(displayName)
                ? displayName : (!TextUtils.isEmpty(email) ? email : uid));
        txtName.setTextColor(0xffffffff);
        txtName.setTextSize(14);
        row.addView(txtName);

        if (!TextUtils.isEmpty(email)) {
            TextView txtEmail = new TextView(this);
            txtEmail.setText(email);
            txtEmail.setTextColor(0xff00afff);
            txtEmail.setTextSize(11);
            row.addView(txtEmail);
        }

        TextView txtUid = new TextView(this);
        txtUid.setText("UID: " + uid);
        txtUid.setTextColor(0xff666666);
        txtUid.setTextSize(10);
        row.addView(txtUid);

        if (!TextUtils.isEmpty(subLine)) {
            TextView txtSub = new TextView(this);
            txtSub.setText(subLine);
            txtSub.setTextColor(0xffaaaaaa);
            txtSub.setTextSize(11);
            row.addView(txtSub);
        }
    }

    private CheckBox makeCheckBox(String label, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setChecked(checked);
        cb.setTextColor(0xffffffff);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(16);
        cb.setLayoutParams(p);
        return cb;
    }

    private TextView sectionHeader(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xffffff00);
        t.setTextSize(14);
        return t;
    }

    private TextView emptyText(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xff888888);
        t.setTextSize(12);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private LinearLayout.LayoutParams matchWidth(int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = topMargin;
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
