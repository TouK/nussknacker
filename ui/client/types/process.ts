import {Edge} from "./edge"
import {NodeType} from "./node"
import {ValidationResult} from "./validation"

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

export type CustomAction = {
  name: string,
  allowedStateStatusNames: Array<string>,
  icon: string | null
}

export type ProcessDefinitionData = {
  nodesConfig?: $TodoType,
  nodesToAdd?: NodesGroup[],
  processDefinition?: $TodoType,
  customActions?: Array<CustomAction>,
  defaultAsyncInterpretation?: boolean,
}
