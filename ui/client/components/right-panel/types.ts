import {ProcessType} from "../Process/types"

type FeatureSettingsState = $TodoType

interface ProcessPropertiesState {
  isSubprocess: boolean,
}

interface ProcessToDisplayState {
  properties?: ProcessPropertiesState,
}

type FetchedProcessDetailsState = ProcessType

export interface GraphState {
  fetchedProcessDetails?: FetchedProcessDetailsState,
  processToDisplay?: ProcessToDisplayState,
  businessView: boolean,
}

export interface SettingsState {
  featuresSettings: FeatureSettingsState,
}
