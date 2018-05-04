import _ from "lodash";

export const visualizationRouterPath = '/visualization/:processId'

export function visualizationUrl(processId, nodeId, edgeId) {
  if (!_.isEmpty(nodeId) && !_.isEmpty(edgeId)) {
    throw new Error("cannot visualize both nodeId and edgeId")
  }
  const baseUrl = `/visualization/${processId}`
  const nodeIdPart = nodeId ? this.nodeIdPart(nodeId) : ""
  const edgeIdPart = edgeId ? this.edgeIdPart(edgeId) : ""
  return baseUrl + nodeIdPart + edgeIdPart
}

export function nodeIdPart(nodeId) {
  return `?nodeId=${nodeId}`
}

export function edgeIdPart(edgeId) {
  return `?edgeId=${edgeId}`
}

export function extractVisualizationParams(queryParams) {
  const urlNodeId = queryParams.nodeId
  const urlEdgeId = queryParams.edgeId
  return {urlNodeId, urlEdgeId}
}