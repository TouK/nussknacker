import {GroupType} from "./groups"

type Type = "Properties" | "_group" | "SubprocessInput" | string

export type LayoutData = { x: number, y: number }

export type NodeType = {
  id: string,
  type: Type,
  isSubprocess?: boolean,
  isDisabled?: boolean,
  additionalFields?: {
    description: string,
    layoutData?: LayoutData,
    groups?: GroupType[],
    properties: {
      layout?: string,
    },
  },
  parameters?: Parameter[],
  branchParameters?: $TodoType,
  branchParametersTemplate?: $TodoType,
  ref?: $TodoType,
  varName?: string,
  value?: $TodoType,
  fields?: Array<Field>,
  outputName?: string
}

export type Field = {
  name: string,
  expression: Expression,
}

export type Parameter = {
    name: string,
    expression: Expression,
}

export type Expression = {
    language: string,
    expression: string,
}

//TODO: Add other process properties...
export type PropertiesType = NodeType & {
  type: "Properties",
}

export type NodeId = NodeType["id"]

export type UINodeType = NodeType | GroupType
