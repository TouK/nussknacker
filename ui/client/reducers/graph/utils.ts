import {cloneDeep, map, reject, without, zipWith} from "lodash"
import {Layout, NodePosition, NodesWithPositions} from "../../actions/nk"
import ProcessUtils from "../../common/ProcessUtils"
import {ExpressionLang} from "../../components/graph/node-modal/editors/expression/types"
import NodeUtils from "../../components/graph/NodeUtils"
import {Edge, EdgeType, NodeId, NodeType, Process, ProcessDefinitionData} from "../../types"
import {GraphState} from "./types"


export function displayNode(state: GraphState, node: NodeType): GraphState {
  return {
    ...state,
    nodeToDisplay: node,
  }
}

export function updateLayoutAfterNodeIdChange(layout: Layout, oldId: NodeId, newId: NodeId): Layout {
  return map(layout, (n) => oldId === n.id ? {...n, id: newId} : n)
}

export function updateAfterNodeIdChange(layout: Layout, process: Process, oldId: NodeId, newId: NodeId) {
  const newLayout = updateLayoutAfterNodeIdChange(layout, oldId, newId)
  return {
    processToDisplay: process,
    layout: newLayout,
  }
}

export function updateAfterNodeDelete(state: GraphState, idToDelete: NodeId) {
  return {
    ...state,
    processToDisplay: {
      ...state.processToDisplay,
      nodes: state.processToDisplay.nodes.filter((n) => n.id !== idToDelete)
    },
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
