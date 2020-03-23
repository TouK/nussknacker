import {Edge} from "./edge"
import {NodeType} from "./node"

export type Process = {
  nodes: NodeType[],
  edges: Edge[],
  properties: $TodoType,
}

export type ProcessId = string

export type ProcessDefinitionData = {
  nodesConfig: $TodoType,
}
