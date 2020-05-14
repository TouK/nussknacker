import {GroupType} from "./groups"

type Type = "Properties" | "_group" | "SubprocessInput" | string

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
  branchParameters?: $TodoType,
  branchParametersTemplate?: $TodoType,
  subprocessVersions?: $TodoType,
  ref?: $TodoType,
}

export type PropertiesType = NodeType & {
  type: "Properties",
}

export type NodeId = NodeType["id"]

export type UINodeType = NodeType | GroupType
