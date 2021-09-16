import {createSelector} from "reselect"
import {isArchived, getProcessCategory} from "./graph"
import {getLoggedUser} from "./settings"

export const getCapabilities = createSelector(
  getLoggedUser, getProcessCategory, isArchived, (loggedUser, processCategory, isArchived) => ({
    write: loggedUser.canWrite(processCategory) && !isArchived,
    editFrontend: loggedUser.canEditFrontend(processCategory) && !isArchived,
    deploy: loggedUser.canDeploy(processCategory) && !isArchived,
    change: loggedUser.canWrite(processCategory),
  }),
)

