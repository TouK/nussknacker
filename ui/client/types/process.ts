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

export type NodeCategory = string

export type PossibleNode = {
  categories: NodeCategory[],
  node: NodeType,
  label: string,
  type: string,
}

export type NodesGroup = {
  possibleNodes: PossibleNode[],
  name: string,
}

export type ProcessDefinitionData = {
  nodesConfig?: $TodoType,
  nodesToAdd?: NodesGroup[],
  processDefinition?: $TodoType,
  servicesDefinition?: ServicesDefinition
}

export type ServicesDefinition = {
  defaultAsyncInterpretation: Boolean
}
