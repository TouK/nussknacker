type Type = "Properties" | "SubprocessInput" | string

export type LayoutData = { x: number, y: number }

//FIXME: something wrong here, process and node mixed?
export type NodeType<F extends Field = Field> = {
  id: string,
  type: Type,
  isSubprocess?: boolean,
  isDisabled?: boolean,
  additionalFields?: {
    description: string,
    layoutData?: LayoutData,
    properties: {
      layout?: string,
    },
  },
  parameters?: Parameter[],
  branchParameters?: $TodoType,
  branchParametersTemplate?: $TodoType,
  subprocessVersions?: $TodoType,
  ref?: $TodoType,
  varName?: string,
  value?: $TodoType,
  fields?: Array<F>,
  outputName?: string,
}

export type SubprocessNodeType = NodeType

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

export type UINodeType = NodeType
