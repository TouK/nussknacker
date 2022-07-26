import {Action, Reducer} from "../actions/reduxTypes";
import {persistReducer} from "redux-persist";
import storage from "redux-persist/lib/storage";


export type BackendNotificationState = {
    processedNotificationIds: string[]
}

const emptyNotifications: BackendNotificationState = {
    processedNotificationIds: []
}

const reducer: Reducer<BackendNotificationState> = (state: BackendNotificationState = emptyNotifications, action: Action) => {
    switch (action.type) {
        case "UPDATE_BACKEND_NOTIFICATIONS":
            return {processedNotificationIds: state.processedNotificationIds.filter(action.notificationIds.includes)}
        case "MARK_BACKEND_NOTIFICATION_READ":
            return {processedNotificationIds: [...state.processedNotificationIds, action.notificationId]}
        default:
            return state
    }
}

export const backendNotifications = persistReducer({key: `notifications`, storage}, reducer)
