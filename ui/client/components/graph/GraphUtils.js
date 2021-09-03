import NodeUtils from "./NodeUtils"
import _ from "lodash"

export function mapProcessWithNewNode(process, before, after) {
  return {
    ...process,
    edges: _.map(process.edges, (e) => {
      if (_.isEqual(e.from, before.id)) {
        return {...e, from: after.id, to: e.to}
      } else if (_.isEqual(e.to, before.id)) {
        return {...e, from: e.from, to: after.id}
      } else {
        return e
      }
    }),
    nodes: _.map(process.nodes, (n) => { return _.isEqual(n, before) ? after : mapBranchParametersWithNewNode(before.id, after.id, n) }),
    properties: NodeUtils.nodeIsProperties(before) ? after : process.properties,
  }
}

//we do mapping here, because we validate changed process before closing modal, and before applying state change in reducer.
function mapBranchParametersWithNewNode(beforeId, afterId, node) {
  if (beforeId !== afterId && node.branchParameters?.find(bp => bp.branchId === beforeId)) {
    const newNode = _.cloneDeep(node)
    const branchParameter = newNode.branchParameters.find(bp => bp.branchId === beforeId)
    if (branchParameter) {
      branchParameter.branchId = afterId
    }
    return newNode
  } else {
    return node
  }
}

export function mapProcessWithNewEdge(process, before, after) {
  return {
    ...process,
    edges: _.map(process.edges, (e) => {
      if (_.isEqual(e.from, before.from) && _.isEqual(e.to, before.to)) {
        return after
      } else {
        return e
      }
    }),
  }
}

export function deleteNode(process, id) {
  return {
    ...process,
    edges: _.filter(process.edges, (e) => !_.isEqual(e.from, id) && !_.isEqual(e.to, id)),
    nodes: _.filter(process.nodes, (n) => !_.isEqual(n.id, id)),
  }
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
    edges: _.reject(process.edges, (e) => e.from === fromId && e.to === toId),
  }
}

function addEdge(process, fromId, toId) {
  return {
    ...process,
    edges: process.edges.concat({from: fromId, to: toId}),
  }
}
