import {Moment} from "moment"
import HttpService from "../../http/HttpService"
import {ThunkAction} from "../reduxTypes"
import {ProcessCounts} from "../../reducers/graph"

export interface DisplayProcessCountsAction {
  processCounts: ProcessCounts,
  type: "DISPLAY_PROCESS_COUNTS",
}

export function displayProcessCounts(processCounts: ProcessCounts): DisplayProcessCountsAction {
  return {
    type: "DISPLAY_PROCESS_COUNTS",
    processCounts,
  }
}

export function fetchAndDisplayProcessCounts(processName: string, from: Moment, to: Moment): ThunkAction<Promise<DisplayProcessCountsAction>> {
  return (dispatch) => HttpService.fetchProcessCounts(processName, from, to)
    .then((response) => dispatch(displayProcessCounts(response.data)))
}
