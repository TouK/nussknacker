import {NodeId, NodeType, LayoutData} from "./index"

export type GroupNodeType = {
  id: GroupId,
  ids: NodeId[],
  nodes: NodeType[],
  type: "_group",
}

export type GroupType = {
  id: string,
  type: "_group",
  nodes: NodeId[],
  layoutData?: LayoutData,
  expanded?: boolean,
}

export type GroupId = GroupType["id"]
