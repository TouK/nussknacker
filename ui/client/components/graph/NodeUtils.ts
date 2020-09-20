/* eslint-disable i18next/no-literal-string */
import _ from "lodash"
import fp from "lodash/fp"
import * as ProcessDefinitionUtils from "../../common/ProcessDefinitionUtils"
import ProcessUtils from "../../common/ProcessUtils"
import {
  Edge,
  GroupId,
  GroupNodeType,
  GroupType,
  NodeId,
  NodeType,
  Process,
  PropertiesType,
  ProcessDefinitionData,
  EdgeType, UINodeType,
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

  isPlainNode = (node: UINodeType) => {
    return !_.isEmpty(node) && !this.nodeIsProperties(node)
  }

  nodeIsGroup = (node: UINodeType): node is GroupType => {
    return node && this.nodeType(node) === "_group"
  }

  nodeIsJoin = (node: NodeType): boolean => {
    return node && this.nodeType(node) === "Join"
  }

  nodesFromProcess = (process: Process, expandedGroups?: GroupId[]): NodeType[] => {
    let nodes = process.nodes
    const groups = this.getCollapsedGroups(process, expandedGroups)
    groups.forEach(group => {
      nodes = nodes.filter(node => !group.nodes.includes(node.id))
      nodes = nodes.concat([this.createGroupNode(process.nodes, group)])
    })
    return nodes
  }

  createGroupNode = (nodes: NodeType[], group: GroupType): GroupNodeType => {
    const groupId = group.id
    const groupNodes = nodes.filter(node => group.nodes.includes(node.id))
    return {
      id: groupId,
      type: "_group",
      nodes: groupNodes,
      ids: group.nodes,
    }
  }

  edgesFromProcess = (process: Process, expandedGroups?: GroupId[]) => {
    let edges = process.edges
    const groups = this.getCollapsedGroups(process, expandedGroups)
    groups.forEach(group => {
      const id = group.id
      edges = edges.map(edge => ({
        ...edge,
        from: _.includes(group.nodes, edge.from) ? id : edge.from,
        to: _.includes(group.nodes, edge.to) ? id : edge.to,
      })).filter(a => !_.eq(a.from, a.to))
    })
    return edges
  }

  getNodeById = (nodeId: NodeId, process: Process) => this.nodesFromProcess(process).find(n => n.id === nodeId)

  getEdgeById = (edgeId: NodeId, process: Process) => this.edgesFromProcess(process).find(e => this.edgeId(e) === edgeId)

  getAllNodesById = (nodeIds: NodeId[], process: Process) => this.nodesFromProcess(process).filter(node => _.includes(nodeIds, node.id))

  containsOnlyPlainNodesWithoutGroups = (nodeIds: NodeId[], process: Process): boolean => {
    return _.every(nodeIds, nodeId => {
      const node = this.getNodeById(nodeId, process)
      return this.isPlainNode(node) && !this.nodeIsGroup(node)
    })
  }

  isAvailable = (node: NodeType, processDefinitionData, category): boolean => {
    const availableIdsInCategory = ProcessDefinitionUtils.getFlatNodesToAddInCategory(processDefinitionData, category)
      .map(nodeToAdd => ProcessUtils.findNodeDefinitionIdOrType(nodeToAdd.node))
    const nodeDefinitionId = ProcessUtils.findNodeDefinitionIdOrType(node)
    return availableIdsInCategory.includes(nodeDefinitionId)
  }

  getIncomingEdges = (nodeId: NodeId, process: Process): Edge[] => this.edgesFromProcess(process).filter(e => e.to === nodeId)

  getEdgesForConnectedNodes = (nodeIds: NodeId[], process: Process): Edge[] => this.edgesFromProcess(process)
    .filter(edge => nodeIds.includes(edge.from) && nodeIds.includes(edge.to))

  getAllGroups = (process: Process): GroupType[] => {
    const groups: GroupType[] = process?.properties?.additionalFields?.groups || []
    return groups.filter(g => g.nodes.some(n => process.nodes.find(({id}) => id == n)))
  }

  getCollapsedGroups = (process: Process, expandedGroups: GroupId[]) => this.getAllGroups(process)
    .filter(g => !_.includes(expandedGroups, g.id))

  getExpandedGroups = (process: Process, expandedGroups: GroupId[]) => this.getAllGroups(process)
    .filter(g => _.includes(expandedGroups, g.id))

  edgeType = (allEdges: Edge[], node: NodeType, processDefinitionData: ProcessDefinitionData): EdgeType => {
    const edgesForNode = this.edgesForNode(node, processDefinitionData)

    if (edgesForNode.canChooseNodes) {
      return edgesForNode.edges[0]
    } else {
      const currentConnectionsTypes = allEdges.filter((edge) => edge.from === node.id).map(e => e.edgeType)
      return edgesForNode.edges.find(et => !currentConnectionsTypes.find(currentType => _.isEqual(currentType, et)))
    }
  }

  createGroup = (process: Process, newGroup: NodeId[]) => {
    const groupId = newGroup.join("-")
    return this._update(
      "properties.additionalFields.groups",
      (groups) => _.concat(groups || [], [{id: groupId, nodes: newGroup}]),
      process,
    )
  }

  ungroup = (process: Process, groupToDeleteId: NodeId) => {
    return this._update<Process, GroupType[]>(
      "properties.additionalFields.groups",
      (groups) => groups.filter(e => !_.isEqual(e.id, groupToDeleteId)),
      process,
    )
  }

  editGroup = (process: Process, oldGroupId: NodeId, newGroup) => {
    const groupForState: GroupType = {id: newGroup.id, nodes: newGroup.ids, type: "_group"}
    return this._update<Process, GroupType[]>(
      "properties.additionalFields.groups",
      (groups) => _.concat(groups.filter(g => g.id !== oldGroupId), [groupForState]),
      process,
    )
  }

  updateGroupsAfterNodeIdChange = (process: Process, oldNodeId: NodeId, newNodeId: NodeId) => {
    return this._changeGroupNodes(process, (nodes) => nodes.map((n) => n === oldNodeId ? newNodeId : n))
  }

  updateGroupsAfterNodeDelete = (process: Process, idToDelete: NodeId) => {
    return this._changeGroupNodes(process, (nodes) => nodes.filter((n) => n !== idToDelete))
  }

  edgesForNode = (node: NodeType, processDefinitionData, forInput?) => {
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

    //TODO: should this map be here??
    const edgeTypeToLabel = {
      FilterFalse: "false",
      FilterTrue: "true",
      SwitchDefault: "default",
      SubprocessOutput: edgeType?.name,
      NextSwitch: edgeType?.condition?.expression,
    }
    return edgeTypeToLabel[edgeType?.type] || ""
  }

  //we don't allow multi outputs other than split, filter, switch and no multiple inputs
  //TODO remove type (Source, Sink) comparisons
  canMakeLink = (fromId, toId, process, processDefinitionData) => {
    const nodeInputs = this._nodeInputs(toId, process)
    const nodeOutputs = this._nodeOutputs(fromId, process)

    const to = this.getNodeById(toId, process)
    const from = this.getNodeById(fromId, process)
    return fromId !== toId &&
      this._canHaveMoreInputs(to, nodeInputs, processDefinitionData) &&
      this._canHaveMoreOutputs(from, nodeOutputs, processDefinitionData)
  }

  //TODO: this function should already exists in lodash?
  // eslint-disable-next-line @typescript-eslint/ban-types
  _update = <T extends {}, U>(path: string, fun: (u: U) => U, object: T): T => {
    return fp.set(path, fun(_.get(object, path)), object)
  }

  _changeGroupNodes = (processToDisplay: Process, nodeOperation) => {
    return this._update<Process, GroupType[]>(
      "properties.additionalFields.groups",
      (groups) => (groups || []).map(group => ({
        ...group,
        nodes: nodeOperation(group.nodes),
      })),
      processToDisplay,
    )
  }

  _canHaveMoreInputs = (nodeTo, nodeInputs, processDefinitionData) => {
    const edgesForNode = this.edgesForNode(nodeTo, processDefinitionData, true)
    const maxEdgesForNode = edgesForNode.edges.length
    return this.hasInputs(nodeTo) && (edgesForNode.canChooseNodes || nodeInputs.length < maxEdgesForNode)
  }

  _canHaveMoreOutputs = (node, nodeOutputs, processDefinitionData) => {
    const edgesForNode = this.edgesForNode(node, processDefinitionData, false)
    const maxEdgesForNode = edgesForNode.edges.length
    return this.hasOutputs(node) && (edgesForNode.canChooseNodes || nodeOutputs.length < maxEdgesForNode)
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

  groupIncludesOneOfNodes = (nodeGroup, nodeIds: NodeId[]) => _.some(
    nodeGroup.nodes,
    (node) => _.includes(nodeIds, node?.id || node),
  )

  groupIncludesAllOfNodes = (nodeGroup, nodeIds: NodeId[]) => _.every(
    nodeGroup.nodes,
    (node) => _.includes(nodeIds, node?.id || node),
  )

  nodesAreInOneGroup = (process: Process, nodeIds: NodeId[]) => _.some(
    this.getAllGroups(process),
    (group) => this.groupIncludesAllOfNodes(group, nodeIds),
  )

  noInputNodeTypes = ["Source", "SubprocessInputDefinition"]

  noOutputNodeTypes = ["Sink", "SubprocessOutputDefinition"]

  //TODO: methods below should be based on backend data, e.g. Subprocess can have outputs or not - based on individual subprocess...
  hasInputs = (node: NodeType) => !this.noInputNodeTypes.some((nodeType) => _.isEqual(nodeType, node?.type))

  hasOutputs = (node: NodeType) => !this.noOutputNodeTypes.some((nodeType) => _.isEqual(nodeType, node?.type))

}

//TODO this pattern is not necessary, just export every public function as in actions.js
export default new NodeUtils()
