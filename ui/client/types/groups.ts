import {NodeId, NodeType} from "./index"

export type GroupNodeType = NodeType & {
  nodes: NodeType[],
  ids: NodeId[],
}

export type GroupType = {
  id: string,
  nodes: NodeId[],
}
export type GroupId = GroupType["id"]
