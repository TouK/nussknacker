import history from "../../../history"
import HttpService from "../../../http/HttpService"
import {getProcessId, getProcessToDisplay, isProcessRenamed} from "../../../reducers/selectors/graph"
import {ThunkAction} from "../../reduxTypes"
import * as UndoRedoActions from "../../undoRedoActions"
import {displayProcessActivity} from "../displayProcessActivity"
import {displayCurrentProcessVersion} from "../process"

async function renameProcess(processName: string, newProcessName: string) {
  if (await HttpService.changeProcessName(processName, newProcessName)) {
    history.replace({
      ...history.location,
      pathname: history.location.pathname.replace(processName, newProcessName),
    })
  }
}

export function saveProcess(comment: string): ThunkAction {
  return async (dispatch, getState) => {
    const state = getState()
    const processId = getProcessId(state)
    const {newId, ...processJson} = getProcessToDisplay(state)

    // save changes before rename and force same processId everywhere
    await HttpService.saveProcess(processId, {...processJson, id: processId}, comment)

    if (isProcessRenamed(state)) {
      await renameProcess(processId, newId)
      await dispatch(displayCurrentProcessVersion(newId))
      await dispatch(displayProcessActivity(newId))
    } else {
      await dispatch(displayCurrentProcessVersion(processId))
      await dispatch(displayProcessActivity(processId))
    }
    await dispatch(UndoRedoActions.clear())
  }
}
