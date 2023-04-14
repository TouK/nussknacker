import HttpService from "../../http/HttpService"
import {ProcessId} from "../../types"
import {ThunkAction} from "../reduxTypes"

export function importFiles(processId: ProcessId, files: File[]): ThunkAction {
  return (dispatch) => {
    files.forEach(async (file: File) => {
      try {
        dispatch({type: "PROCESS_LOADING"})
        const process = await HttpService.importProcess(processId, file)
        dispatch({type: "UPDATE_IMPORTED_PROCESS", processJson: process.data})
      } catch (error) {
        dispatch({type: "LOADING_FAILED"})
      }
    })
  }
}
