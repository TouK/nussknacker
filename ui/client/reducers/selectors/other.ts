import {createSelector} from "reselect"
import {getProcessCategory, isArchived} from "./graph"
import {getLoggedUser} from "./settings"

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

