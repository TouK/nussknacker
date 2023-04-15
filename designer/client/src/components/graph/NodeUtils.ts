/* eslint-disable i18next/no-literal-string */
import _, {uniqBy} from "lodash"
import * as ProcessDefinitionUtils from "../../common/ProcessDefinitionUtils"
import ProcessUtils from "../../common/ProcessUtils"
import {
  Edge,
  EdgeKind,
  EdgeType,
  NodeId,
  NodeType,
  Process,
  ProcessDefinitionData,
  PropertiesType,
  SubprocessNodeType,
  UINodeType,
} from "../../types"
import {UnknownRecord} from "../../types/common"

class NodeUtils {

  isNode = (obj: UnknownRecord): obj is NodeType => {
    return !_.isEmpty(obj) && _.has(obj, "id") && _.has(obj, "type")
  }

  nodeType = (node: NodeType) => {
    return node.type ? node.type : "Properties"
  }

  nodeIsProperties = (node: UINodeType): node is PropertiesType => {
    const type = node && this.nodeType(node)
    return type === "Properties"
  }

  nodeIsSubprocess = (node): node is SubprocessNodeType => {
    return this.nodeType(node) === "SubprocessInput"
  }

  isPlainNode = (node: UINodeType) => {
    return !_.isEmpty(node) && !this.nodeIsProperties(node)
  }

  nodeIsJoin = (node: NodeType): boolean => {
    return node && this.nodeType(node) === "Join"
  }

  nodesFromProcess = (process: Process): NodeType[] => process.nodes || []

  edgesFromProcess = (process: Process) => process.edges || []

  getProcessProperties = ({id, properties}: Process, name?: string) => ({id: name || id, ...properties})

  getNodeById = (nodeId: NodeId, process: Process) => this.nodesFromProcess(process).find(n => n.id === nodeId)

  getEdgeById = (edgeId: NodeId, process: Process) => this.edgesFromProcess(process).find(e => this.edgeId(e) === edgeId)

  getAllNodesById = (nodeIds: NodeId[], process: Process) => {
    const allNodes = this.nodesFromProcess(process).filter(node => nodeIds.includes(node.id))
    return uniqBy(allNodes, n => n.id)
  }

  getAllNodesByIdWithEdges = (ids: NodeId[], process: Process) => {
    const nodes = this.getAllNodesById(ids, process)
    const edgesForNodes = this.getEdgesForConnectedNodes(nodes.map(n => n.id), process)
    return {
      nodes: nodes,
      edges: edgesForNodes,
    }
  }

  isAvailable = (node: NodeType, processDefinitionData, category): boolean => {
    const availableIdsInCategory = ProcessDefinitionUtils.getFlatCategoryComponents(processDefinitionData, category)
      .map(component => ProcessUtils.findNodeDefinitionIdOrType(component.node))
    const nodeDefinitionId = ProcessUtils.findNodeDefinitionIdOrType(node)
    return availableIdsInCategory.includes(nodeDefinitionId)
  }

  getIncomingEdges = (nodeId: NodeId, process: Process): Edge[] => this.edgesFromProcess(process).filter(e => e.to === nodeId)

  getEdgesForConnectedNodes = (
    nodeIds: NodeId[],
    process: Process,
  ): Edge[] => process.edges?.filter(edge => nodeIds.includes(edge.from) && nodeIds.includes(edge.to))

  edgeType = (allEdges: Edge[], node: NodeType, processDefinitionData: ProcessDefinitionData): EdgeType => {
    const edgesForNode = this.edgesForNode(node, processDefinitionData)

    if (edgesForNode.canChooseNodes) {
      return edgesForNode.edges[0]
    } else {
      const currentConnectionsTypes = allEdges.filter((edge) => edge.from === node.id).map(e => e.edgeType)
      return edgesForNode.edges.find(et => !currentConnectionsTypes.find(currentType => _.isEqual(currentType, et)))
    }
  }

  edgesForNode = (node: NodeType, processDefinitionData: ProcessDefinitionData, forInput?: boolean) => {
    const nodeObjectTypeDefinition = ProcessUtils.findNodeDefinitionId(node)
    //TODO: when we add more configuration for joins, probably more complex logic will be needed
    const data = processDefinitionData.edgesForNodes
      .filter(e => !forInput || e.isForInputDefinition === forInput)
      //here we use == in second comparison, as we sometimes compare null to undefined :|
      .find(e => e.nodeId.type === _.get(node, "type") && e.nodeId.id == nodeObjectTypeDefinition)
    return data || {edges: [null], canChooseNodes: false}
  }

  edgeLabel = (edge: Edge) => {
    const edgeType = edge?.edgeType
    const type = edgeType?.type
    switch (type) {
      case EdgeKind.subprocessOutput:
        return edgeType?.name
      case EdgeKind.switchNext:
        return edgeType?.condition?.expression
    }
    return this.edgeTypeLabel(type)
  }

  //TODO: i18next
  edgeTypeLabel = (type: string) => {
    switch (type) {
      case EdgeKind.filterFalse:
        return "🔴 false"
      case EdgeKind.filterTrue:
        return "🟢 true"
      case EdgeKind.switchDefault:
        return "default (deprecated)"
      case EdgeKind.switchNext:
        return "condition"
      default:
        return ""
    }
  }

  //we don't allow multi outputs other than split, filter, switch and no multiple inputs
  //TODO remove type (Source, Sink) comparisons
  canMakeLink = (fromId: NodeId, toId: NodeId, process: Process, processDefinitionData: ProcessDefinitionData, previousEdge?: Edge): boolean => {
    const nodeInputs = this._nodeInputs(toId, process)
    //we do not want to include currently edited edge
    const nodeOutputs = this._nodeOutputs(fromId, process)
      .filter(e => e.from !== previousEdge?.from && e.to !== previousEdge?.to)

    const to = this.getNodeById(toId, process)
    const from = this.getNodeById(fromId, process)
    if (fromId !== toId) {
      const canHaveMoreInputs = this.canHaveMoreInputs(to, nodeInputs, processDefinitionData)
      const canHaveMoreOutputs = this._canHaveMoreOutputs(from, nodeOutputs, processDefinitionData)
      const isUniqe = !nodeInputs.find(e => e.from === fromId)
      return canHaveMoreInputs && canHaveMoreOutputs && isUniqe
    }

    return false
  }

  canHaveMoreInputs = (node: NodeType, nodeInputs: Edge[], processDefinitionData: ProcessDefinitionData): boolean => {
    const edgesForNode = this.edgesForNode(node, processDefinitionData, true)
    const maxEdgesForNode = edgesForNode.edges.length
    return this.hasInputs(node) && (edgesForNode.canChooseNodes || nodeInputs.length < maxEdgesForNode)
  }

  _canHaveMoreOutputs = (node, nodeOutputs, processDefinitionData): boolean => {
    const edgesForNode = this.edgesForNode(node, processDefinitionData, false)
    const maxEdgesForNode = edgesForNode.edges.length
    return this.hasOutputs(node) && (edgesForNode.canChooseNodes || nodeOutputs.filter(e => e.to).length < maxEdgesForNode)
  }

  _nodeInputs = (nodeId: NodeId, process: Process) => {
    return this.edgesFromProcess(process).filter(e => e.to == nodeId)
  }

  _nodeOutputs = (nodeId: NodeId, process: Process) => {
    return this.edgesFromProcess(process).filter(e => e.from == nodeId)
  }

  edgeId = (edge: Edge): string => {
    return `${edge.from}-${edge.to}`
  }

  noInputNodeTypes = ["Source", "SubprocessInputDefinition"]

  noOutputNodeTypes = ["Sink", "SubprocessOutputDefinition"]

  //TODO: methods below should be based on backend data, e.g. Subprocess can have outputs or not - based on individual subprocess...
  hasInputs = (node: NodeType) => !this.noInputNodeTypes.some((nodeType) => _.isEqual(nodeType, node?.type))

  hasOutputs = (node: NodeType) => !this.noOutputNodeTypes.some((nodeType) => _.isEqual(nodeType, node?.type))

}

//TODO this pattern is not necessary, just export every public function as in actions.js
export default new NodeUtils()
