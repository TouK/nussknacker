import history from "../../../history"
import HttpService from "../../../http/HttpService"
import {getProcessToDisplay, isProcessRenamed} from "../../../reducers/selectors/graph"
import {ThunkAction} from "../../reduxTypes"
import * as UndoRedoActions from "../../undoRedoActions"
import {displayProcessActivity} from "../displayProcessActivity"
import {displayCurrentProcessVersion} from "../process"

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

    const isRenamed = isProcessRenamed(state) && await doRenameProcess(processJson.id, processJson.newId)
    const processId = isRenamed ? processJson.newId : processJson.id

    await dispatch(displayCurrentProcessVersion(processId))
    await dispatch(displayProcessActivity(processId))
    await dispatch(UndoRedoActions.clear())
  }
}
