import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getMessaging } from "firebase-admin/messaging";
import { onValueCreated } from "firebase-functions/v2/database";
import { HttpsError, onCall } from "firebase-functions/v2/https";

initializeApp();

const ADMIN_EMAIL = "ps152390@gmail.com";

export const notifyAdminOfPendingRequest = onValueCreated(
  {
    ref: "/pendingRequests/{uid}",
    instance: "pramod-octapad-loop-default-rtdb",
    region: "asia-southeast1",
  },
  async () => {
    await getMessaging().send({
      topic: "admin-new-requests",
      notification: {
        title: "New access request",
        body: "A user requested app access. Open the Admin Panel to review it.",
      },
    });
  },
);

export const listRegisteredUsers = onCall(
  { region: "asia-southeast1" },
  async (request) => {
    const email = request.auth?.token.email;
    if (typeof email !== "string" || email.toLowerCase() !== ADMIN_EMAIL) {
      throw new HttpsError("permission-denied", "Only the Admin Panel account can list users.");
    }

    const pageToken = request.data?.pageToken;
    if (pageToken !== undefined && typeof pageToken !== "string") {
      throw new HttpsError("invalid-argument", "pageToken must be a string.");
    }

    const result = await getAuth().listUsers(1000, pageToken);
    return {
      users: result.users
        .filter((user) => user.email?.toLowerCase() !== ADMIN_EMAIL)
        .map((user) => ({
          uid: user.uid,
          email: user.email ?? "",
          displayName: user.displayName ?? "",
        })),
      nextPageToken: result.pageToken ?? null,
    };
  },
);
