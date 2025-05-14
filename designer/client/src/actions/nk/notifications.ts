export type NotificationActions =
    | {
          type: "UPDATE_BACKEND_NOTIFICATIONS";
          notificationIds: string[];
      }
    | {
          type: "MARK_BACKEND_NOTIFICATION_READ";
          notificationId: string;
      };

export function updateBackendNotifications(notificationIds: string[]): NotificationActions {
    return {
        type: "UPDATE_BACKEND_NOTIFICATIONS",
        notificationIds: notificationIds,
    };
}

export function markBackendNotificationRead(notificationId: string): NotificationActions {
    return {
        type: "MARK_BACKEND_NOTIFICATION_READ",
        notificationId: notificationId,
    };
}
