import { initializeApp } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";
import { onValueCreated } from "firebase-functions/v2/database";

initializeApp();

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
