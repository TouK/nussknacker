import {Edge, Process} from "../../../types"

export function isEdgeConnected(edge: Edge): boolean {
  return !!edge.from && !!edge.to
}

export function withoutEmptyEdges(processJson: Process): Process {
  return {
    ...processJson,
    edges: processJson.edges.filter(isEdgeConnected),
  }
}
