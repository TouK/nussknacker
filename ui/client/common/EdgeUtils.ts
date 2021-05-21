import {Edge} from "../types"

const EDITABLE_EDGES = [
  "NextSwitch",
  "SwitchDefault",
]

export function isEdgeEditable(edge: Edge): boolean {
  const edgeType = edge?.edgeType?.type
  return edgeType && EDITABLE_EDGES.includes(edgeType)
}
