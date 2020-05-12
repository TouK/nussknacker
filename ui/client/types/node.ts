import {GroupType} from "./groups"

type Type = "Properties" | "_group" | string

export type LayoutData = { x: number, y: number }

export type NodeType = {
  id: string,
  type: Type,
  isSubprocess?: boolean,
  isDisabled?: boolean,
  additionalFields?: {
    description: $TodoType,
    layoutData?: LayoutData,
    groups?: GroupType[],
    properties: {
      layout?: string,
    },
  },
}

export type NodeId = NodeType["id"]
