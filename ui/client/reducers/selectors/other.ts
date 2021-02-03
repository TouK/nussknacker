import {createSelector} from "reselect"
import {getIsArchived, getProcessCategory} from "./graph"
import {getLoggedUser} from "./settings"

export const getCapabilities = createSelector(
  getLoggedUser, getProcessCategory, getIsArchived, (loggedUser, processCategory, isArchived) => ({
    write: loggedUser.canWrite(processCategory) && !isArchived,
    deploy: loggedUser.canDeploy(processCategory) && !isArchived,
    change: loggedUser.canWrite(processCategory),
  }),
)

