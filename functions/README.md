# New access request notifications

The Admin Panel subscribes to the `admin-new-requests` FCM topic after an
administrator opens the dashboard. This function sends a generic push whenever
a new child is created under `pendingRequests`.

Deploy it to the Firebase project configured in `.firebaserc`:

```sh
npm ci --prefix functions
firebase login
firebase deploy --only functions --project pramod-octapad-loop
```

Cloud Functions deployment requires a Firebase project with billing enabled
and access to deploy functions. Registered profiles are loaded directly from the
Realtime Database `users` path in the Admin Panel.
Android 13 and later also require the admin to allow notifications for the
Admin Panel app.
