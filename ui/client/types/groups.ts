import {NodeId, NodeType, LayoutData} from "./index"

export type GroupNodeType = NodeType & {
  nodes: NodeType[],
  ids: NodeId[],
}

export type GroupType = {
  id: string,
  nodes: NodeId[],
  layoutData?: LayoutData,
  expanded?: boolean,
}
export type GroupId = GroupType["id"]
