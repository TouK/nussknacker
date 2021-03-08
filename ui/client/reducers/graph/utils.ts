import {ExpressionLang} from "../../components/graph/node-modal/editors/expression/types"
import {GraphState} from "./types"
import {NodeType, NodeId, Process, GroupType, Edge, EdgeType, ProcessDefinitionData} from "../../types"
import NodeUtils from "../../components/graph/NodeUtils"
import {map, zipWith, omit, cloneDeep, reject} from "lodash"
import {Layout, NodesWithPositions, NodePosition} from "../../actions/nk"
import ProcessUtils from "../../common/ProcessUtils"

function isBetween(id1: NodeId, id2: NodeId): (e: Edge) => boolean {
  return ({from, to}) => from == id1 && to == id2 || from == id2 && to == id1
}

function canGroup(state: GraphState, newNode: NodeType | GroupType): boolean {
  const {groupingState, processToDisplay: {edges}} = state
  const isGroup = NodeUtils.nodeIsGroup(newNode)
  return !isGroup && groupingState.length === 0 || !!groupingState.find(id => edges.find(isBetween(id, newNode.id)))
}

export function displayOrGroup(state: GraphState, node: NodeType, readonly = false): GraphState {
  if (state.groupingState) {
    return {
      ...state,
      groupingState: canGroup(state, node) ?
        state.groupingState.concat(node.id) :
        state.groupingState,
    }
  }
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
  const {layout, processToDisplay: {nodes}} = state

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
  if (nodeToDelete.type === "SubprocessInput") {
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
