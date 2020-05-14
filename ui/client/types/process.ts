import {Edge} from "./edge"
import {NodeType} from "./node"
import {ValidationResult} from "../actions/nk"

export type Process = {
  nodes: NodeType[],
  edges: Edge[],
  properties: NodeType,
  validationResult: ValidationResult,
}

export type ProcessId = string

export type ProcessDefinitionData = {
  nodesConfig?: $TodoType,
  processDefinition?: $TodoType,
}
