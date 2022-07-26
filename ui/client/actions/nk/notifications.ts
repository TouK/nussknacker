import {ThunkAction} from "../reduxTypes";

export type NotificationActions =
  | UpdateBackendNotifications
  | MarkBackendNotificationRead

export type UpdateBackendNotifications = {
    type: "UPDATE_BACKEND_NOTIFICATIONS",
    notificationIds: string[]
}

export function updateBackendNotifications(notificationIds: string[]): ThunkAction {
    return (dispatch) => {
        return dispatch({
            type: "UPDATE_BACKEND_NOTIFICATIONS",
            notificationIds: notificationIds
        })
    }
}

export type MarkBackendNotificationRead = {
    type: "MARK_BACKEND_NOTIFICATION_READ",
    notificationId: string
}

export function markBackendNotificationRead(notificationId: string): ThunkAction {
    return (dispatch) => {
        return dispatch({
            type: "MARK_BACKEND_NOTIFICATION_READ",
            notificationId: notificationId
        })
    }
}
