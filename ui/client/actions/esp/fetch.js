import {dateFormat} from "../../config"
import HttpService from "../../http/HttpService"
import {displayProcessCounts} from "./process"

export function fetchProcessDefinition(processingType, isSubprocess, subprocessVersions) {
  return (dispatch) => {
    return HttpService.fetchProcessDefinitionData(processingType, isSubprocess, subprocessVersions).then((response) => (
            dispatch({type: "PROCESS_DEFINITION_DATA", processDefinitionData: response.data})
        ),
    )
  }
}

export function fetchAvailableQueryStates() {
  return (dispatch) => {
    return HttpService.availableQueryableStates().then((response) =>
        dispatch({type: "AVAILABLE_QUERY_STATES", availableQueryableStates: response.data}),
    )
  }
}

export function fetchAndDisplayProcessCounts(processName, from, to) {
  return (dispatch) =>
      HttpService.fetchProcessCounts(
          processName,
          from ? from.format(dateFormat) : null,
          to ? to.format(dateFormat) : null,
      ).then((response) => dispatch(displayProcessCounts(response.data)))
}

