import {NodeId} from "./node"

export type EdgeType = {
  type: $TodoType,
  name: $TodoType,
  condition: $TodoType,
}

export type Edge = {
  from: NodeId,
  to: NodeId,
  edgeType?: EdgeType,
}
