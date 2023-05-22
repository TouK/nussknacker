import HttpService from "../../http/HttpService";
import {ProcessId} from "../../types";
import {ProcessActionType, ProcessVersionType} from "../../components/Process/types";
import {ThunkAction} from "../reduxTypes";

export type LoadProcessVersionsAction = {
  type: "PROCESS_VERSIONS_LOADED",
  history: ProcessVersionType[],
  lastAction: ProcessActionType,
  lastDeployedAction: ProcessActionType,
}

export function loadProcessVersions(processId: ProcessId): ThunkAction<Promise<LoadProcessVersionsAction>> {
  return (dispatch) => HttpService.fetchProcessDetails(processId).then(response => {
    return dispatch({
      type: "PROCESS_VERSIONS_LOADED",
      history: response.data.history,
      lastDeployedAction: response.data.lastDeployedAction,
      lastAction: response.data.lastAction
    })
  })
}
