import {Action, Reducer} from "../actions/reduxTypes";
import {persistReducer} from "redux-persist";
import storage from "redux-persist/lib/storage";
import {uniq} from "lodash";


export type BackendNotificationState = {
    processedNotificationIds: string[]
}

const emptyNotifications: BackendNotificationState = {
    processedNotificationIds: []
}

const reducer: Reducer<BackendNotificationState> = (state: BackendNotificationState = emptyNotifications, action: Action) => {
    switch (action.type) {
        case "UPDATE_BACKEND_NOTIFICATIONS":
            return {processedNotificationIds: uniq(state.processedNotificationIds.filter(k => action.notificationIds.includes(k)))}
        case "MARK_BACKEND_NOTIFICATION_READ":
            return {processedNotificationIds: uniq([...state.processedNotificationIds, action.notificationId])}
        default:
            return state
    }
}

export const backendNotifications = persistReducer({key: `notifications`, storage}, reducer)
