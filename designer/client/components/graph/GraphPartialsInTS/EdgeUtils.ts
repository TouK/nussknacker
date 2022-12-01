import {Edge, Process} from "../../../types"

export function isEdgeConnected(edge: Edge): boolean {
  return !!edge.from && !!edge.to
}

//TODO: handle "piggybacking" of edges with different mechanism than empty edges
export function withoutHackOfEmptyEdges(processJson: Process): Process {
  return {
    ...processJson,
    edges: processJson.edges.filter(isEdgeConnected),
  }
}
