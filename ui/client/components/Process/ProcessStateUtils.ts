import {ActionType, ProcessStateType, ProcessType} from "./types"
import {
  descriptionProcessArchived,
  descriptionSubprocess,
  descriptionSubprocessArchived,
  unknownDescription,
  unknownTooltip,
} from "./messages"
import {absoluteBePath} from "../../common/UrlUtils"

export const unknownIcon = "/assets/states/status-unknown.svg"
const subprocessIcon = "/assets/process/subprocess.svg"
const archivedIcon = "/assets/process/archived.svg"

class ProcessStateUtils {

  public canDeploy = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Deploy)

  public canCancel = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Cancel)

  public canArchive = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Archive)

  getStateDescription(
    {isArchived, isSubprocess, state}: ProcessType,
    processState: ProcessStateType,
    isStateLoaded: boolean,
  ): string {
    if (isArchived) {
      return isSubprocess ?
        descriptionSubprocessArchived() :
        descriptionProcessArchived()
    }

    if (isSubprocess) {
      return descriptionSubprocess()
    }

    return isStateLoaded ?
      processState?.description || unknownDescription() :
      state?.description || unknownDescription()
  }

  getStatusIcon(
    {isArchived, isSubprocess, state}: ProcessType,
    processState: ProcessStateType,
    isStateLoaded: boolean
  ): string {
    if (isArchived) {
      return absoluteBePath(archivedIcon)
    }

    if (isSubprocess) {
      return absoluteBePath(subprocessIcon)
    }

    if (isStateLoaded) {
      return absoluteBePath(processState?.icon || unknownIcon)
    }

    return absoluteBePath(state?.icon || unknownIcon)
  }

  getStatusTooltip(
    {isArchived, isSubprocess, state}: ProcessType,
    processState: ProcessStateType,
    isStateLoaded: boolean
  ): string {
    if (isArchived) {
      return isSubprocess ?
        descriptionSubprocessArchived() :
        descriptionProcessArchived()
    }

    if (isSubprocess) {
      return descriptionSubprocess()
    }

    if (isStateLoaded) {
      return processState?.tooltip || unknownTooltip()
    }

    return state?.tooltip || unknownTooltip()
  }

  getTransitionKey(
    {id, isArchived, isSubprocess, state}: ProcessType,
    processState: ProcessStateType,
  ): string {
    if (isArchived || isSubprocess) {
      return `${id}`
    }
    return `${id}-${processState?.icon || state?.icon || unknownIcon}`
  }
}

export default new ProcessStateUtils()

