import {NodeType} from "../../../../types"

export function refParameters(editedNode: NodeType): { name: string }[] {
  return editedNode.ref.parameters || []
}

export function serviceParameters(editedNode: NodeType): { name: string }[] {
  return editedNode.service.parameters || []
}
