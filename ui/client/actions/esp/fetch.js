//@flow
import type Moment from "moment"
import {dateFormat} from "../../config"
import HttpService from "../../http/HttpService"
import type {ThunkAction} from "../types"
import {availableQueryStates, displayProcessCounts, processDefinitionData} from "./process"

type ProcessingType = string

export function fetchProcessDefinition(processingType: ProcessingType, isSubprocess: boolean, subprocessVersions: $FlowTODO): ThunkAction {
  return (dispatch) => {
    return HttpService.fetchProcessDefinitionData(processingType, isSubprocess, subprocessVersions).then((response) => (
            dispatch(processDefinitionData(response.data))
        ),
    )
  }
}

export function fetchAvailableQueryStates(): ThunkAction {
  return (dispatch) => {
    return HttpService.availableQueryableStates().then((response) =>
        dispatch(availableQueryStates(response.data)),
    )
  }
}

export function fetchAndDisplayProcessCounts(processName: string, from: Moment, to: Moment): ThunkAction {
  return (dispatch) =>
      HttpService.fetchProcessCounts(
          processName,
          from ? from.format(dateFormat) : null,
          to ? to.format(dateFormat) : null,
      ).then((response) => dispatch(displayProcessCounts(response.data)))
}