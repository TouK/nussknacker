import history from "../../../history"
import HttpService from "../../../http/HttpService"
import {getProcessToDisplay, getProcessUnsavedNewName, isProcessRenamed} from "../../../reducers/selectors/graph"
import {ThunkAction} from "../../reduxTypes"
import * as UndoRedoActions from "../../undoRedoActions"
import {displayProcessActivity} from "../displayProcessActivity"
import {displayCurrentProcessVersion} from "../process"
import {loadProcessToolbarsConfiguration} from "../loadProcessToolbarsConfiguration"

async function doRenameProcess(processName: string, newProcessName: string) {
  const isSuccess = await HttpService.changeProcessName(processName, newProcessName)
  if (isSuccess) {
    history.replace({
      ...history.location,
      pathname: history.location.pathname.replace(processName, newProcessName),
    })
  }
  return isSuccess
}

export function saveProcess(comment: string): ThunkAction {
  return async (dispatch, getState) => {
    const state = getState()
    const processJson = getProcessToDisplay(state)

    // save changes before rename and force same processId everywhere
    await HttpService.saveProcess(processJson.id, processJson, comment)

    const unsavedNewName = getProcessUnsavedNewName(state)
    const isRenamed = isProcessRenamed(state) && await doRenameProcess(processJson.id, unsavedNewName)
    const processId = isRenamed ? unsavedNewName : processJson.id

    await dispatch(UndoRedoActions.clear())
    await dispatch(displayCurrentProcessVersion(processId))
    await dispatch(displayProcessActivity(processId))
    if (isRenamed) {
      await dispatch(loadProcessToolbarsConfiguration(processId))
    }
  }
}
