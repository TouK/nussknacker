import {ProcessType} from "../Process/types"
import {NodeId} from "../../actions/nk/models"

type FeatureSettingsState = $TodoType

export type ProcessPropertiesState = {
  id: NodeId,
  isSubprocess: boolean,

}

export type ProcessToDisplayState = {
  properties?: ProcessPropertiesState,
}

type NodeToDisplay = $TodoType

type SelectionState = string[]

type HistoryAction = $TodoType

type History = {
  past: HistoryAction[],
  future: HistoryAction[],
}

export type GraphState = {
  fetchedProcessDetails?: ProcessType,
  processToDisplay?: ProcessToDisplayState,
  businessView: boolean,
  nodeToDisplay: NodeToDisplay,
  selectionState?: SelectionState,
  history: History,
}

export type SettingsState = {
  featuresSettings: FeatureSettingsState,
}
