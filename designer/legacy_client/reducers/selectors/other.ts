import {createSelector} from "reselect"
import {getFetchedProcessDetails, getProcessCategory, isArchived} from "./graph"
import {getLoggedUser} from "./settings"
import {RootState} from "../index";
import {NotificationsState} from "react-notification-system-redux";
import {BackendNotificationState} from "../notifications";
import {getProcessState, isProcessStateLoaded} from "./scenarioState";

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

export const getFetchedProcessState = createSelector(
  getFetchedProcessDetails,
  isProcessStateLoaded,
  getProcessState,
  (fetchedProcessDetails, isStateLoaded, processState) => isStateLoaded ? processState : fetchedProcessDetails?.state,
)
