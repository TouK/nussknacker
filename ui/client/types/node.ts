type Type = "Properties" | "_group" | string

export type NodeType = {
  id: string,
  type: Type,
  isSubprocess?: boolean,
  isDisabled?: boolean,
  additionalFields?: {description: $TodoType},
}

export type NodeId = NodeType["id"]
