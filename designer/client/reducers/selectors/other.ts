import {createSelector} from "reselect"
import {getProcessCategory, isArchived} from "./graph"
import {getLoggedUser} from "./settings"
import {RootState} from "../index"
import {NotificationsState} from "react-notification-system-redux"
import {BackendNotificationState} from "../notifications"

export interface Capabilities {
  write?: boolean,
  editFrontend?: boolean,
  deploy?: boolean,
  change?: boolean,
}

export const getCapabilities = createSelector(
  getLoggedUser, getProcessCategory, isArchived, (loggedUser, processCategory, isArchived): Capabilities => ({
    write: loggedUser.canWrite(processCategory) && !isArchived,
    editFrontend: loggedUser.canEditFrontend(processCategory) && !isArchived,
    deploy: loggedUser.canDeploy(processCategory) && !isArchived,
    change: loggedUser.canWrite(processCategory),
  }),
)

export const getNotifications = (state: RootState): NotificationsState => state.notifications
export const getBackendNotifications = (state: RootState): BackendNotificationState => state.backendNotifications
