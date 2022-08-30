import {Edge, EdgeKind} from "../types"

const EDITABLE_EDGES: string[] = [
  EdgeKind.switchNext,
  EdgeKind.switchDefault,
  EdgeKind.filterFalse,
  EdgeKind.filterTrue,
  EdgeKind.subprocessOutput,
]

export function isEdgeEditable(edge?: Edge): boolean {
  const edgeType = edge?.edgeType?.type
  return edgeType && EDITABLE_EDGES.includes(edgeType)
}
