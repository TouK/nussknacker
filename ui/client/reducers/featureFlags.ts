import {persistReducer} from "redux-persist"
import storage from "redux-persist/lib/storage"
import {Reducer} from "../actions/reduxTypes"

export interface FeatureFlags {
  showDeploymentsInCounts?: boolean,
}

const reducer: Reducer<FeatureFlags> = (state = {}, action) => {
  switch (action.type) {
    case "SET_FLAGS":
      return action.flags
    case "TOGGLE_FLAGS":
      return action.keys.reduce((value, key) => ({...value, [key]: !state[key]}), state)
    default:
      return state
  }
}

export const featureFlags = persistReducer({key: `ff`, storage}, reducer)
