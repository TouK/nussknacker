import HttpService from "../../http/HttpService"
import {$TodoType} from "../migrationTypes"
import {ThunkAction} from "../reduxTypes"

export type AvailableQueryStatesAction = {
  type: "AVAILABLE_QUERY_STATES";
  availableQueryableStates: $TodoType;
}

export function availableQueryStates(data: $TodoType): AvailableQueryStatesAction {
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
