import {FeatureFlags} from "../../reducers/featureFlags"

type ToggleFlagsAction = {type: "TOGGLE_FLAGS", keys: Array<keyof FeatureFlags>}
type SetFlagsAction = {type: "SET_FLAGS", flags: FeatureFlags}

export function toggleFlags(keys: Array<keyof FeatureFlags>): ToggleFlagsAction {
  return {
    type: "TOGGLE_FLAGS",
    keys,
  }
}

export function setFlags(flags: FeatureFlags): SetFlagsAction {
  return {
    type: "SET_FLAGS",
    flags,
  }
}

export type FeatureFlagsActions =
  | ToggleFlagsAction
  | SetFlagsAction
