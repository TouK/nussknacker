type Type = "Properties" | "SubprocessInput" | string

export type LayoutData = { x: number, y: number }

export interface BranchParams {
  branchId: string,
  parameters: Field[],
}

export type BranchParametersTemplate = $TodoType

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
  branchParameters?: BranchParams[],
  branchParametersTemplate?: BranchParametersTemplate,
  subprocessVersions?: $TodoType,
  ref?: {
    id: string,
    typ: string,
    parameters: $TodoType[],
  },
  varName?: string,
  value?: $TodoType,
  fields?: Array<F>,
  outputName?: string,
  service?: {
    id: string,
    parameters?: $TodoType[],
  },
  typeSpecificProperties?: {
    type: $TodoType,
  },
  nodeType: string,
  [key: string]: any,
}

export type SubprocessNodeType = NodeType

export type Field = {
  name: string,
  expression: Expression,
}

export interface Parameter {
  name: string,
  expression: Expression,
  typ?: unknown,
}

export interface Expression {
  language: string,
  expression: string,
}

//TODO: Add other process properties...
export type PropertiesType = NodeType & {
  type: "Properties",
}

export type NodeId = NodeType["id"]

export type UINodeType = NodeType
