import {ExpressionObj} from "../components/graph/node-modal/editors/expression/types"
import {NodeId} from "./node"

export enum EdgeKind {
  filterFalse = "FilterFalse",
  filterTrue = "FilterTrue",
  switchDefault = "SwitchDefault",
  switchNext = "NextSwitch",
  subprocessOutput = "SubprocessOutput",
}

export type EdgeType = {
  type: EdgeKind,
  name?: string,
  condition?: ExpressionObj,
}

export type Edge = {
  from: NodeId,
  to: NodeId,
  edgeType?: EdgeType,
}
