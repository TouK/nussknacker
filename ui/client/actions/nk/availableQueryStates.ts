import HttpService from "../../http/HttpService"
import {$FlowTODO} from "../migrationTypes"
import {ThunkAction} from "../reduxTypes"

export type AvailableQueryStatesAction = {
  type: "AVAILABLE_QUERY_STATES",
  availableQueryableStates: $FlowTODO,
}

export function availableQueryStates(data: $FlowTODO): AvailableQueryStatesAction {
  return {
    type: "AVAILABLE_QUERY_STATES",
    availableQueryableStates: data,
  }
}

export function fetchAvailableQueryStates(): ThunkAction {
  return (dispatch) => {
    return HttpService.availableQueryableStates().then((response) =>
        dispatch(availableQueryStates(response.data)),
    )
  }
}