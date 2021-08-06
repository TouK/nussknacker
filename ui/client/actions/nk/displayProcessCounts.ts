import {Moment} from "moment"
import {DATE_FORMAT} from "../../config"
import HttpService from "../../http/HttpService"
import {ThunkAction} from "../reduxTypes"
import {ProcessCounts} from "../../reducers/graph"

export function displayProcessCounts(processCounts: ProcessCounts): $TodoType {
  return {
    type: "DISPLAY_PROCESS_COUNTS",
    processCounts,
  }
}

export function fetchAndDisplayProcessCounts(processName: string, from: Moment, to: Moment): ThunkAction<Promise<void>> {
  return (dispatch) => HttpService.fetchProcessCounts(
    processName, from, to).then((response) => dispatch(displayProcessCounts(response.data)))
}
