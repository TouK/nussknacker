import {ThunkAction} from "../reduxTypes"
import {reportEvent} from "./reportEvent"
import {events} from "../../analytics/TrackingEvents"
import {clear as clearUndo} from "./../undoRedoActions"
import {Process, ProcessId} from "../../types"
import HttpService from "./../../http/HttpService"
import {ProcessVersionId} from "../../components/Process/types"
import {displayProcessActivity} from "./displayProcessActivity"

export function fetchProcessToDisplay(processId: ProcessId, versionId?: ProcessVersionId) {
  return (dispatch) => {
    dispatch({
      type: "PROCESS_FETCH",
    })

    return HttpService.fetchProcessDetails(processId, versionId).then((response) => {
      dispatch(displayTestCapabilities(response.data.json))
      return dispatch({
        type: "DISPLAY_PROCESS",
        fetchedProcessDetails: response.data,
      })
    })
  }
}

export function loadProcessState(processId: ProcessId): ThunkAction {
  return (dispatch) => HttpService.fetchProcessState(processId).then(({data}) => dispatch({
    type: "PROCESS_STATE_LOADED",
    processState: data,
  }))
}

export function displayTestCapabilities(processDetails: Process) {
  return (dispatch) => HttpService.getTestCapabilities(processDetails).then(
    ({data}) => dispatch({
      type: "UPDATE_TEST_CAPABILITIES",
      capabilities: data,
    })
  )
}

export function displayCurrentProcessVersion(processId: ProcessId) {
  return fetchProcessToDisplay(processId)
}

export function clearProcess(): ThunkAction {
  return (dispatch) => {
    dispatch(clearUndo())
    dispatch({type: "CLEAR_PROCESS"})
  }
}

export function hideRunProcessDetails(): ThunkAction {
  return (dispatch) => {
    dispatch(reportEvent({
      category: events.categories.rightPanel,
      action: events.actions.buttonClick,
      name: "hide",
    }))
    dispatch({type: "HIDE_RUN_PROCESS_DETAILS"})
  }
}

export function addAttachment(processId: ProcessId, processVersionId: ProcessVersionId, file: File) {
  return (dispatch) => HttpService.addAttachment(processId, processVersionId, file).then(
    () => dispatch(displayProcessActivity(processId))
  )
}
