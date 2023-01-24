import {RootState} from "../index"
import {NotificationsState} from "react-notification-system-redux"
import {BackendNotificationState} from "../notifications"

export const getNotifications = (state: RootState): NotificationsState => state.notifications
export const getBackendNotifications = (state: RootState): BackendNotificationState => state.backendNotifications
