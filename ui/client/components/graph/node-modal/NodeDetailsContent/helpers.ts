import {NodeType, Parameter} from "../../../../types"

export function refParameters(editedNode: NodeType): Parameter[] {
  return editedNode.ref.parameters || []
}

export function serviceParameters(editedNode: NodeType): Parameter[] {
  return editedNode.service.parameters || []
}
