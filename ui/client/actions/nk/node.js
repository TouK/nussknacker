import HttpService from "../../http/HttpService"

export function displayNodeDetails(node) {
  return {
    type: "DISPLAY_NODE_DETAILS",
    nodeToDisplay: node,
  }
}

export function deleteNodes(ids) {
  return runSyncActionsThenValidate(state => [{
    type: "DELETE_NODES",
    ids: ids,
  }])
}

export function nodesConnected(fromNode, toNode) {
  return runSyncActionsThenValidate(state => [
    {
      type: "NODES_CONNECTED",
      fromNode: fromNode,
      toNode: toNode,
      processDefinitionData: state.settings.processDefinitionData,
    },
  ])
}

export function nodesDisconnected(from, to) {
  return runSyncActionsThenValidate(state => [{
    type: "NODES_DISCONNECTED",
    from: from,
    to: to,
  }])
}

export function injectNode(from, middle, to, edgeType) {
  return runSyncActionsThenValidate(state => [
    {
      type: "NODES_DISCONNECTED",
      from: from.id,
      to: to.id,
    },
    {
      type: "NODES_CONNECTED",
      fromNode: from,
      toNode: middle,
      processDefinitionData: state.settings.processDefinitionData,
      edgeType: edgeType,
    },
    {
      type: "NODES_CONNECTED",
      fromNode: middle,
      toNode: to,
      processDefinitionData: state.settings.processDefinitionData,
    },
  ])
}

export function nodeAdded(node, position) {
  return {
    type: "NODE_ADDED",
    node: node,
    position: position,
  }
}

export function nodesWithEdgesAdded(nodesWithPositions, edges) {
  return (dispatch, getState) => dispatch({
    type: "NODES_WITH_EDGES_ADDED",
    nodesWithPositions,
    edges,
    processDefinitionData: getState().settings.processDefinitionData,
  })
}

//this WON'T work for async actions - have to handle promises separately
function runSyncActionsThenValidate(syncActions) {
  return (dispatch, getState) => {
    syncActions(getState()).forEach(action => dispatch(action))
    return HttpService.validateProcess(getState().graphReducer.processToDisplay).then(
      (response) => dispatch({type: "VALIDATION_RESULT", validationResult: response.data}),
    )
  }
}
