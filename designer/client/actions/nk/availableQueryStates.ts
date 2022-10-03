import HttpService from "../../http/HttpService"
import {ThunkAction} from "../reduxTypes"

type AvailableQueryableStates = $TodoType

export type AvailableQueryStatesAction = {
  type: "AVAILABLE_QUERY_STATES",
  availableQueryableStates: AvailableQueryableStates,
}

export function availableQueryStates(data: AvailableQueryableStates): AvailableQueryStatesAction {
  return {
    type: "AVAILABLE_QUERY_STATES",
    availableQueryableStates: data,
  }
}

export function fetchAvailableQueryStates(): ThunkAction {
  return (dispatch) => {
    return HttpService.availableQueryableStates().then((response) => dispatch(availableQueryStates(response.data)))
  }
}
