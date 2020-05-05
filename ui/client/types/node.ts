type Type = "Properties" | "_group" | string

export type NodeType = {
  id: string,
  type: Type,
  isSubprocess?: boolean,
  isDisabled?: boolean,
  additionalFields?: {
    description: $TodoType,
    properties: {
      layout?: string,
      expandedGroups?: string,
    },
  },
}

export type NodeId = NodeType["id"]
