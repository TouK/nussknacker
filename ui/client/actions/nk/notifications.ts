export type NotificationActions =
    | UpdateBackendNotifications
    | MarkBackendNotificationRead

export type UpdateBackendNotifications = {
    type: "UPDATE_BACKEND_NOTIFICATIONS",
    notificationIds: string[]
}

export function updateBackendNotifications(notificationIds: string[]): NotificationActions {
    return {
        type: "UPDATE_BACKEND_NOTIFICATIONS",
        notificationIds: notificationIds
    }
}

export type MarkBackendNotificationRead = {
    type: "MARK_BACKEND_NOTIFICATION_READ",
    notificationId: string
}

export function markBackendNotificationRead(notificationId: string): NotificationActions {
    return {
        type: "MARK_BACKEND_NOTIFICATION_READ",
        notificationId: notificationId
    }
}
