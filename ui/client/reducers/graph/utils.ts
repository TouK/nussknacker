import {cloneDeep, map, omit, reject, without, zipWith} from "lodash"
import {Layout, NodePosition, NodesWithPositions} from "../../actions/nk"
import ProcessUtils from "../../common/ProcessUtils"
import {ExpressionLang} from "../../components/graph/node-modal/editors/expression/types"
import NodeUtils from "../../components/graph/NodeUtils"
import {Edge, EdgeType, GroupType, NodeId, NodeType, Process, ProcessDefinitionData} from "../../types"
import {GraphState} from "./types"

function isConnectedTo(...nodeIds: NodeId[]) {
  return (edge: Edge) => {
    return nodeIds.find(id => edge.from === id || edge.to === id)
  }
}

function graphDFS(edges: Record<NodeId, NodeId[]>) {
  const [startNode] = Object.keys(edges)
  const queue = [startNode]
  const visited = [startNode]
  while (queue.length) {
    const current = queue.pop()
    edges[current]
      .filter(node => !visited.includes(node))
      .forEach(node => {
        visited.push(node)
        queue.push(node)
      })
  }
  return visited
}

function isGraphConnected(nodes: NodeId[], edges: Edge[]): boolean {
  const connections = Object.fromEntries(nodes.map(nodeId => [
    nodeId,
    edges
      .filter(isConnectedTo(nodeId))
      .filter(isConnectedTo(...without(nodes, nodeId)))
      .map(({from, to}) => nodeId === from ? to : from),
  ]))
  return graphDFS(connections).length === nodes.length
}

function getSelectedNodes(state: GraphState): NodeType[] {
  const {processToDisplay, selectionState = []} = state
  return NodeUtils.nodesWithGroups(processToDisplay).filter(n => selectionState.includes(n.id))
}

export function getSelectedGroups(state: GraphState): GroupType[] {
  return getSelectedNodes(state).filter(NodeUtils.nodeIsGroup)
}

function nodeInGroup(state: GraphState) {
  const groups = NodeUtils.getAllGroups(state.processToDisplay)
  return node => !!groups.find(g => g.nodes.includes(node))
}

export function canGroupSelection(state: GraphState): boolean {
  const {processToDisplay: {edges}, selectionState = []} = state

  if (selectionState.length < 2) {
    return false
  }

  const containsGroup = getSelectedGroups(state).length
  if (containsGroup || selectionState.some(nodeInGroup(state))) {
    return false
  }

  return isGraphConnected(selectionState, edges)
}

export function displayOrGroup(state: GraphState, node: NodeType, readonly = false): GraphState {
  return {
    ...state,
    nodeToDisplay: node,
    nodeToDisplayReadonly: readonly,
  }
}

export function updateLayoutAfterNodeIdChange(layout: Layout, oldId: NodeId, newId: NodeId): Layout {
  return map(layout, (n) => oldId === n.id ? {...n, id: newId} : n)
}

export function updateAfterNodeIdChange(layout: Layout, process: Process, oldId: NodeId, newId: NodeId) {
  const newLayout = updateLayoutAfterNodeIdChange(layout, oldId, newId)
  const withGroupsUpdated = NodeUtils.updateGroupsAfterNodeIdChange(process, oldId, newId)
  return {
    processToDisplay: withGroupsUpdated,
    layout: newLayout,
  }
}

export function updateAfterNodeDelete(state: GraphState, idToDelete: NodeId) {
  return {
    ...state,
    processToDisplay: NodeUtils.updateGroupsAfterNodeDelete(state.processToDisplay, idToDelete),
    layout: state.layout.filter(n => n.id !== idToDelete),
  }
}

function generateUniqueNodeId(initialId: NodeId, usedIds: NodeId[], nodeCounter: number, isCopy: boolean): NodeId {
  const newId = isCopy ? `${initialId} (copy ${nodeCounter})` : `${initialId} ${nodeCounter}`
  return usedIds.includes(newId) ? generateUniqueNodeId(initialId, usedIds, nodeCounter + 1, isCopy) : newId
}

function createUniqueNodeId(initialId: NodeId, usedIds: NodeId[], isCopy: boolean): NodeId {
  return initialId && !usedIds.includes(initialId) ?
    initialId :
    generateUniqueNodeId(initialId, usedIds, 1, isCopy)
}

function getUniqueIds(initialIds: string[], alreadyUsedIds: string[], isCopy: boolean) {
  return initialIds.reduce((uniqueIds, initialId) => {
    const reservedIds = alreadyUsedIds.concat(uniqueIds)
    const uniqueId = createUniqueNodeId(initialId, reservedIds, isCopy)
    return uniqueIds.concat(uniqueId)
  }, [])
}

export function prepareNewNodesWithLayout(
  state: GraphState,
  nodesWithPositions: NodesWithPositions,
  isCopy: boolean,
): { layout: NodePosition[], nodes: NodeType[], uniqueIds?: NodeId[] } {
  const {layout, processToDisplay: {nodes = []}} = state

  const alreadyUsedIds = nodes.map(node => node.id)
  const initialIds = nodesWithPositions.map(nodeWithPosition => nodeWithPosition.node.id)
  const uniqueIds = getUniqueIds(initialIds, alreadyUsedIds, isCopy)

  const updatedNodes = zipWith(nodesWithPositions, uniqueIds, ({node}, id) => ({...node, id}))
  const updatedLayout = zipWith(nodesWithPositions, uniqueIds, ({position}, id) => ({id, position}))

  return {
    nodes: [...nodes, ...updatedNodes],
    layout: [...layout, ...updatedLayout],
    uniqueIds,
  }
}

export function addNodesWithLayout(state: GraphState, {nodes, layout}: ReturnType<typeof prepareNewNodesWithLayout>): GraphState {
  return {
    ...state,
    processToDisplay: {
      ...state.processToDisplay,
      nodes,
    },
    layout,
  }
}

export function removeSubprocessVersionForLastSubprocess(processToDisplay: Process, idToDelete: NodeId) {
  const subprocessVersions = processToDisplay.properties.subprocessVersions
  const nodeToDelete = processToDisplay.nodes.find(n => n.id === idToDelete)
  if (nodeToDelete?.type === "SubprocessInput") {
    const subprocessId = nodeToDelete.ref?.id
    const allSubprocessNodes = processToDisplay.nodes.filter(n => n.ref?.id === subprocessId)
    const isLastOne = allSubprocessNodes.length === 1
    return isLastOne ? omit(subprocessVersions, subprocessId) : subprocessVersions
  } else {
    return subprocessVersions
  }
}

export function createEdge(
  fromNode: NodeType,
  toNode: NodeType,
  edgeType: EdgeType,
  allEdges: Edge[],
  processDefinitionData: ProcessDefinitionData,
) {
  const baseEdge = {from: fromNode.id, to: toNode.id}
  const adjustedEdgeType = edgeType || NodeUtils.edgeType(allEdges, fromNode, processDefinitionData)
  return adjustedEdgeType ? {...baseEdge, edgeType: adjustedEdgeType} : baseEdge
}

function removeBranchParameter(node: NodeType, branchId: NodeId) {
  const {branchParameters, ...clone} = cloneDeep(node)
  return {
    ...clone,
    branchParameters: reject(branchParameters, parameter => parameter.branchId === branchId),
  }
}

export function adjustBranchParametersAfterDisconnect(nodes: NodeType[], removedEdgeFrom: NodeId, removedEdgeTo: NodeId): NodeType[] {
  const node = nodes.find(n => n.id === removedEdgeTo)
  if (node && NodeUtils.nodeIsJoin(node)) {
    const newToNode = removeBranchParameter(node, removedEdgeFrom)
    return nodes.map((n) => { return n.id === removedEdgeTo ? newToNode : n })
  } else {
    return nodes
  }
}

export function enrichNodeWithProcessDependentData(originalNode: NodeType, processDefinitionData: ProcessDefinitionData, edges: Edge[]) {
  const node = cloneDeep(originalNode)
  if (NodeUtils.nodeIsJoin(node)) {
    const nodeObjectDetails = ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)
    const declaredBranchParameters = nodeObjectDetails.parameters.filter(p => p.branchParam)
    const incomingEdges = edges.filter(e => e.to === node.id)

    node.branchParameters = incomingEdges.map((edge) => {
      const branchId = edge.from
      const existingBranchParams = node.branchParameters.find(p => p.branchId === branchId)
      const newBranchParams = declaredBranchParameters.map((branchParamDef) => {
        const existingParamValue = ((existingBranchParams || {}).parameters || []).find(p => p.name === branchParamDef.name)
        const templateParamValue = (node.branchParametersTemplate || []).find(p => p.name === branchParamDef.name)
        return existingParamValue || cloneDeep(templateParamValue) ||
          // We need to have this fallback to some template for situation when it is existing node and it has't got
          // defined parameters filled. see note in DefinitionPreparer on backend side TODO: remove it after API refactor
          cloneDeep({
            name: branchParamDef.name,
            expression: {
              expression: `#${branchParamDef.name}`,
              language: ExpressionLang.SpEL,
            },
          })
      })
      return {
        branchId: branchId,
        parameters: newBranchParams,
      }
    })
  }
  return node
}
