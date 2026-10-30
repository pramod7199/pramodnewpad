package com.pramod.loopmidi.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private DatabaseReference pendingRef;
    private DatabaseReference usersRef;
    private DatabaseReference deactivatedRef;

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
    private ValueEventListener pendingListener;
    private ValueEventListener activeListener;
    private ValueEventListener deactivatedListener;
    private int refreshPendingReads;
    private int refreshFailureCount;
    private StringBuilder refreshErrors;

    // Checkboxes for the manual-activate section
    private CheckBox cbManualFull;
    private CheckBox cbManualLoops;
    private CheckBox cbManualDrums;

    private enum Filter { PENDING, ACTIVE, DEACTIVATED }
    private Filter currentFilter = Filter.PENDING;

    private final List<UserEntry> allUsers = new ArrayList<>();
    private final List<UserEntry> deactivatedUsers = new ArrayList<>();
    private final List<PendingEntry> pendingRequests = new ArrayList<>();

    private static class UserEntry {
        String uid, email, displayName, deviceToken;
        boolean hasFull, hasLoops, hasDrums;
        boolean isDeactivated;
        long activatedAt;

        boolean hasAnyAppAccess() {
            return hasFull || hasLoops || hasDrums;
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
        buildUi();
        AdminNotifications.registerForNewRequests(this);
        listenPending();
        listenActive();
        listenDeactivated();
    }

    @Override
    protected void onDestroy() {
        if (pendingListener != null) pendingRef.removeEventListener(pendingListener);
        if (activeListener != null) usersRef.removeEventListener(activeListener);
        if (deactivatedListener != null) deactivatedRef.removeEventListener(deactivatedListener);
        super.onDestroy();
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
        cbRow.addView(cbManualFull);
        cbRow.addView(cbManualLoops);
        cbRow.addView(cbManualDrums);
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
            entry.email = child.child("email").getValue(String.class);
            entry.displayName = child.child("displayName").getValue(String.class);
            Long timestamp = child.child("timestamp").getValue(Long.class);
            entry.timestamp = timestamp != null ? timestamp : 0;
            pendingRequests.add(entry);
        }
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
        cbRow.addView(cbFull);
        cbRow.addView(cbLoops);
        cbRow.addView(cbDrums);
        row.addView(cbRow);

        // Action buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(6), 0, 0);

        Button btnActivate = new Button(this);
        btnActivate.setText("Activate");
        btnActivate.setOnClickListener(v ->
                activateUser(uid, email, displayName,
                        cbFull.isChecked(), cbLoops.isChecked(), cbDrums.isChecked()));
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
        applySearchAndFilter();
    }

    private interface SnapshotUpdate {
        void apply(DataSnapshot snapshot);
    }

    private void refreshLists() {
        if (refreshPendingReads > 0) return;
        refreshPendingReads = 3;
        refreshFailureCount = 0;
        refreshErrors = new StringBuilder();
        btnRefresh.setEnabled(false);
        btnRefresh.setText("Refreshing...");

        refreshReference(pendingRef, "Pending", this::updatePending);
        refreshReference(usersRef, "Active", this::updateActive);
        refreshReference(deactivatedRef, "Deactivated", this::updateDeactivated);
    }

    private void refreshReference(DatabaseReference reference, String listName,
                                  SnapshotUpdate update) {
        reference.get().addOnCompleteListener(this, task -> {
            if (isFinishing() || isDestroyed()) return;

            if (task.isSuccessful() && task.getResult() != null) {
                update.apply(task.getResult());
            } else {
                refreshFailureCount++;
                if (refreshErrors.length() > 0) refreshErrors.append(", ");
                refreshErrors.append(listName);
                Exception error = task.getException();
                if (error != null) {
                    android.util.Log.e("AdminDashboard", listName + " refresh failed", error);
                }
            }

            refreshPendingReads--;
            if (refreshPendingReads == 0) {
                btnRefresh.setEnabled(true);
                btnRefresh.setText("Refresh");
                String message = refreshFailureCount == 0
                        ? "Lists refreshed."
                        : "Could not refresh: " + refreshErrors;
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Parse a DataSnapshot into a UserEntry. */
    private UserEntry parseUserEntry(DataSnapshot child, boolean isDeactivated) {
        UserEntry entry = new UserEntry();
        entry.uid = child.getKey();
        entry.email = child.child("email").getValue(String.class);
        entry.displayName = child.child("displayName").getValue(String.class);
        entry.deviceToken = child.child("deviceToken").getValue(String.class);
        entry.isDeactivated = isDeactivated;

        DataSnapshot appsSnap = child.child("allowedApps");
        entry.hasFull  = !appsSnap.exists()
                || Boolean.TRUE.equals(appsSnap.child("full").getValue(Boolean.class));
        entry.hasLoops = !appsSnap.exists()
                || Boolean.TRUE.equals(appsSnap.child("loops").getValue(Boolean.class));
        entry.hasDrums = !appsSnap.exists()
                || Boolean.TRUE.equals(appsSnap.child("drums").getValue(Boolean.class));

        Long ts = child.child("activatedAt").getValue(Long.class);
        entry.activatedAt = ts != null ? ts : 0;
        return entry;
    }

    private void applySearchAndFilter() {
        String query = "";
        if (editSearch != null && editSearch.getText() != null) {
            query = editSearch.getText().toString().trim().toLowerCase();
        }
        String q = query.toLowerCase();

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
            txtDeactivatedEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
        }

        updateFilterButtons();
        if (txtSearchEmpty != null) {
            txtSearchEmpty.setVisibility(!q.isEmpty() && !any ? View.VISIBLE : View.GONE);
        }
    }

    private boolean matchesSearch(String uid, String email, String displayName, String query) {
        return query.isEmpty()
                || (uid != null && uid.toLowerCase().contains(query))
                || (email != null && email.toLowerCase().contains(query))
                || (displayName != null && displayName.toLowerCase().contains(query));
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
        return count;
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
        txtStatus.setText(entry.isDeactivated ? "❌ DEACTIVATED"
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
                        + (entry.hasDrums ? "✅Drums" : "❌Drums")
                        + (entry.deviceToken != null ? "  |  🔒 Device locked" : "  |  🔓 Unlocked"));

        // Action buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(6), 0, 0);

        if (entry.isDeactivated) {
            // Deactivated user → Reactivate button
            Button btnReactivate = new Button(this);
            btnReactivate.setText("Reactivate");
            btnReactivate.setOnClickListener(v -> reactivateUser(entry));
            btnRow.addView(btnReactivate, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else if (noAppAccess) {
            Button btnGrantAccess = new Button(this);
            btnGrantAccess.setText("Grant Access");
            btnGrantAccess.setOnClickListener(v ->
                    showEditAppsDialog(entry.uid, entry.email, entry.displayName,
                            entry.hasFull, entry.hasLoops, entry.hasDrums));
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
                            entry.hasFull, entry.hasLoops, entry.hasDrums));
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

    // ── Firebase actions ──────────────────────────────────────────────────────

    private void activateUser(String uid, String email, String displayName,
                              boolean full, boolean loops, boolean drums) {
        Map<String, Object> data = new HashMap<>();
        data.put("email",       email != null ? email : "");
        data.put("displayName", displayName != null ? displayName : "");
        data.put("activatedAt", System.currentTimeMillis());

        Map<String, Object> appsMap = new HashMap<>();
        appsMap.put("full",  full);
        appsMap.put("loops", loops);
        appsMap.put("drums", drums);
        data.put("allowedApps", appsMap);

        usersRef.child(uid).setValue(data);
        pendingRef.child(uid).removeValue();
        Toast.makeText(this, "Activated: " + (email != null ? email : uid), Toast.LENGTH_SHORT).show();
    }

    private void saveAllowedApps(String uid, String email,
                                 boolean full, boolean loops, boolean drums) {
        Map<String, Object> appsMap = new HashMap<>();
        appsMap.put("full",  full);
        appsMap.put("loops", loops);
        appsMap.put("drums", drums);
        usersRef.child(uid).child("allowedApps").setValue(appsMap);
        Toast.makeText(this, "Access updated: " + (email != null ? email : uid), Toast.LENGTH_SHORT).show();
    }

    /** Deactivate: move from authorizedUsers to deactivatedUsers (instead of deleting). */
    private void confirmDeactivate(UserEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Deactivate karein?")
                .setMessage("Yeh user turant logout ho jayega. Aap ise baad me Reactivate kar sakte hain.")
                .setPositiveButton("Deactivate", (dialog, which) -> {
                    // Copy to deactivatedUsers
                    Map<String, Object> data = new HashMap<>();
                    data.put("email", entry.email != null ? entry.email : "");
                    data.put("displayName", entry.displayName != null ? entry.displayName : "");
                    data.put("deactivatedAt", System.currentTimeMillis());

                    Map<String, Object> appsMap = new HashMap<>();
                    appsMap.put("full", entry.hasFull);
                    appsMap.put("loops", entry.hasLoops);
                    appsMap.put("drums", entry.hasDrums);
                    data.put("allowedApps", appsMap);

                    if (entry.deviceToken != null) {
                        data.put("deviceToken", entry.deviceToken);
                    }

                    deactivatedRef.child(entry.uid).setValue(data);
                    usersRef.child(entry.uid).removeValue();
                    Toast.makeText(this, "Deactivated: " + (entry.email != null ? entry.email : entry.uid),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Reactivate: move from deactivatedUsers back to authorizedUsers. */
    private void reactivateUser(UserEntry entry) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", entry.email != null ? entry.email : "");
        data.put("displayName", entry.displayName != null ? entry.displayName : "");
        data.put("activatedAt", System.currentTimeMillis());

        Map<String, Object> appsMap = new HashMap<>();
        appsMap.put("full", entry.hasFull);
        appsMap.put("loops", entry.hasLoops);
        appsMap.put("drums", entry.hasDrums);
        data.put("allowedApps", appsMap);

        usersRef.child(entry.uid).setValue(data);
        deactivatedRef.child(entry.uid).removeValue();
        Toast.makeText(this, "Reactivated: " + (entry.email != null ? entry.email : entry.uid),
                Toast.LENGTH_SHORT).show();
    }

    private void manualActivate() {
        String uid = editManualUid.getText().toString().trim();
        if (TextUtils.isEmpty(uid)) {
            Toast.makeText(this, "UID daalein.", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("activatedAt", System.currentTimeMillis());
        data.put("note", "manually added");

        Map<String, Object> appsMap = new HashMap<>();
        appsMap.put("full",  cbManualFull.isChecked());
        appsMap.put("loops", cbManualLoops.isChecked());
        appsMap.put("drums", cbManualDrums.isChecked());
        data.put("allowedApps", appsMap);

        usersRef.child(uid).setValue(data);
        editManualUid.setText("");
        Toast.makeText(this, "Activated by UID.", Toast.LENGTH_SHORT).show();
    }

    private void showEditAppsDialog(String uid, String email, String displayName,
                                    boolean curFull, boolean curLoops, boolean curDrums) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);

        CheckBox cbFull  = makeCheckBox("Full  (com.pramod.loopmidi)",        curFull);
        CheckBox cbLoops = makeCheckBox("Loops (com.pramod.loopmidi.loops)",  curLoops);
        CheckBox cbDrums = makeCheckBox("Drums (com.pramod.loopmidi.drums)",  curDrums);
        layout.addView(cbFull);
        layout.addView(cbLoops);
        layout.addView(cbDrums);

        new AlertDialog.Builder(this)
                .setTitle("APK Access — " + (!TextUtils.isEmpty(email) ? email : uid))
                .setView(layout)
                .setPositiveButton("Save", (d, w) ->
                        saveAllowedApps(uid, email,
                                cbFull.isChecked(), cbLoops.isChecked(), cbDrums.isChecked()))
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
