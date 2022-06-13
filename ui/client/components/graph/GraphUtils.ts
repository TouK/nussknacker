import NodeUtils from "./NodeUtils"
import {cloneDeep, isEqual, map, reject} from "lodash"
import {Edge, NodeId, Process} from "../../types"

export function mapProcessWithNewNode(process, before, after) {
  return {
    ...process,
    edges: map(process.edges, (e) => {
      if (isEqual(e.from, before.id)) {
        return {...e, from: after.id, to: e.to}
      } else if (isEqual(e.to, before.id)) {
        return {...e, from: e.from, to: after.id}
      } else {
        return e
      }
    }),
    nodes: map(process.nodes, (n) => {
      return isEqual(n, before) ? after : mapBranchParametersWithNewNode(before.id, after.id, n)
    }),
    properties: NodeUtils.nodeIsProperties(before) ? after : process.properties,
  }
}

//we do mapping here, because we validate changed process before closing modal, and before applying state change in reducer.
function mapBranchParametersWithNewNode(beforeId, afterId, node) {
  if (beforeId !== afterId && node.branchParameters?.find(bp => bp.branchId === beforeId)) {
    const newNode = cloneDeep(node)
    const branchParameter = newNode.branchParameters.find(bp => bp.branchId === beforeId)
    if (branchParameter) {
      branchParameter.branchId = afterId
    }
    return newNode
  } else {
    return node
  }
}

export function mapProcessWithNewEdge(process: Process, before: Edge, after: Edge): Process {
  return {
    ...process,
    edges: map(process.edges, (e) => {
      if (isEqual(e.from, before.from) && isEqual(e.to, before.to)) {
        return after
      } else {
        return e
      }
    }),
  }
}

export function replaceProcessEdges(process: Process, node: NodeId, edges: Edge[]): Process {
  return {
    ...process,
    edges: process.edges.filter((storedEdge) => storedEdge.from !== node).concat(edges),
  }
}

export function deleteNode(process, id) {
  const edges = process.edges.filter((e) => e.from !== id).map((e) => e.to === id ? {...e, to: ""} : e)
  const nodes = process.nodes.filter((n) => n.id !== id)
  return {...process, edges, nodes}
}

export function canInjectNode(process, sourceId, middleManId, targetId, processDefinitionData) {
  const processAfterDisconnection = deleteEdge(process, sourceId, targetId)
  const canConnectSourceToMiddleMan = NodeUtils.canMakeLink(sourceId, middleManId, processAfterDisconnection, processDefinitionData)
  const processWithConnectedSourceAndMiddleMan = addEdge(processAfterDisconnection, sourceId, middleManId)
  const canConnectMiddleManToTarget = NodeUtils.canMakeLink(middleManId, targetId, processWithConnectedSourceAndMiddleMan, processDefinitionData)
  return canConnectSourceToMiddleMan && canConnectMiddleManToTarget
}

function deleteEdge(process, fromId, toId) {
  return {
    ...process,
    edges: reject(process.edges, (e) => e.from === fromId && e.to === toId),
  }
}

function addEdge(process, fromId, toId) {
  return {
    ...process,
    edges: process.edges.concat({from: fromId, to: toId}),
  }
}
