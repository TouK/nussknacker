// @flow

export type AvailableQueryStatesAction = {
  type: "AVAILABLE_QUERY_STATES",
  availableQueryableStates: $FlowTODO,
}

export function availableQueryStates(data: $FlowTODO) {
  return {
    type: "AVAILABLE_QUERY_STATES",
    availableQueryableStates: data,
  }
}