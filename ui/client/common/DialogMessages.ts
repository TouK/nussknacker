import i18next from "i18next"
import {ProcessId} from "../types"

export const unsavedProcessChanges = () => {
  return i18next.t(
    "dialogMessages.unsavedProcessChanges",
    "There are some unsaved process changes. Do you want to discard unsaved changes?",
  )
}

export const deploy = (processId: ProcessId) => {
  return i18next.t("dialogMessages.deploy", "Are you sure you want to deploy {{processId}}?", {processId})
}

export const migrate = (processId, environmentId) => {
  return i18next.t(
    "dialogMessages.migrate",
    "Are you sure you want to migrate {{processId}} to {{environmentId}}?",
    {processId, environmentId},
  )
}

export const stop = (processId: ProcessId) => {
  return i18next.t("dialogMessages.stop", "Are you sure you want to stop {{processId}}?", {processId})
}

export const archiveProcess = (processId: ProcessId) => {
  return i18next.t("dialogMessages.archiveProcess", "Are you sure you want to archive {{processId}}?", {processId})
}

export const unArchiveProcess = (processId: ProcessId) => {
  return i18next.t("dialogMessages.unArchiveProcess", "Are you sure you want to unarchive {{processId}}?", {processId})
}

export const deleteComment = () => {
  return i18next.t("dialogMessages.deleteComment", "Are you sure you want to delete comment?")
}

export const cantArchiveRunningProcess = () => {
  return i18next.t(
    "dialogMessages.cantArchiveRunningProcess",
    "You can't archive running process! Stop it first and then click 'archive' button again.",
  )
}

export const  valueAlreadyTaken = () => {
  return i18next.t(
    "validation.duplicateValue",
    "This value is already taken"
  )
}

export const valueAlreadyTakenDescription = () => {
  return i18next.t(
    "validation.duplicateValueDescription",
    "Please provide new unique value"
  )
}
