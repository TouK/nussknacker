import {ProcessStateType, ProcessType} from "./types"
import {
  descriptionProcessArchived,
  descriptionSubprocess,
  descriptionSubprocessArchived,
  unknownTooltip,
} from "./messages"
import {absoluteBePath} from "../../common/UrlUtils"

export function getStatusIcon(
  {isArchived, isSubprocess, state}: ProcessType,
  processState: ProcessStateType,
  isStateLoaded: boolean
): string {
  if (isArchived) {
    return absoluteBePath("/assets/process/archived.svg")
  }

  if (isSubprocess) {
    return absoluteBePath("/assets/process/subprocess.svg")
  }

  if (isStateLoaded) {
    return absoluteBePath(processState?.icon || "/assets/states/status-unknown.svg")
  }

  return absoluteBePath(state?.icon || "/assets/states/status-unknown.svg")
}

export function getStatusTooltip(
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
