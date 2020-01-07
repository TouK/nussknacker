import _ from "lodash"
import fp from "lodash/fp"
import ProcessUtils from "../../common/ProcessUtils.js"
import * as ProcessDefinitionUtils from "../../common/ProcessDefinitionUtils";

class NodeUtils {

  isNode = (obj) => {
    return !_.isEmpty(obj) && _.has(obj, "id") && _.has(obj, "type")
  }

  nodeType = (node) => {
    return node.type ? node.type : "Properties";
  }

  nodeIsProperties = (node) => {
    const type = node && this.nodeType(node)
    return type === "Properties";
  }

  isPlainNode = (node) => {
    return !_.isEmpty(node) && !this.nodeIsProperties(node)
  }

  nodeIsGroup = (node) => {
    return node && this.nodeType(node) === "_group"
  }

  nodesFromProcess = (process, expandedGroups) => {
    let nodes = process.nodes
    const groups = this.getCollapsedGroups(process, expandedGroups)
    groups.forEach(group => {
      nodes = nodes.filter(node => !_.includes(group.nodes, node.id))
      nodes = nodes.concat([this.createGroupNode(process.nodes, group)])
    })
    return nodes;
  }

  createGroupNode = (nodes, group) => {
    const groupId = group.id
    const groupNodes = nodes.filter(node => _.includes(group.nodes, node.id))
    return {
      id: groupId,
      type: "_group",
      nodes: groupNodes,
      ids: group.nodes
    }
  }

  edgesFromProcess = (process, expandedGroups) => {
    let edges = process.edges
    const groups = this.getCollapsedGroups(process, expandedGroups)
    groups.forEach(group => {
      const id = group.id
      edges = edges.map(edge => ({
        ...edge,
        from: _.includes(group.nodes, edge.from) ? id : edge.from,
        to: _.includes(group.nodes, edge.to) ? id : edge.to,
      })).filter(a => !(_.eq(a.from, a.to)))
    })
    return edges;
  }

  getNodeById = (nodeId, process) => this.nodesFromProcess(process).find(n => n.id === nodeId)

  getEdgeById = (edgeId, process) => this.edgesFromProcess(process).find(e => this.edgeId(e) === edgeId)

  getAllNodesById = (nodeIds, process) => this.nodesFromProcess(process).filter(node => _.includes(nodeIds, node.id))

  containsOnlyPlainNodesWithoutGroups = (nodeIds, process) => {
    return _.every(nodeIds, nodeId => {
      const node = this.getNodeById(nodeId, process)
      return this.isPlainNode(node) && !this.nodeIsGroup(node)
    })
  }

  isAvailable = (node, processDefinitionData, category) => {
    const availableIdsInCategory = ProcessDefinitionUtils.getFlatNodesToAddInCategory(processDefinitionData, category)
      .map(nodeToAdd => ProcessUtils.findNodeDefinitionIdOrType(nodeToAdd.node))
    const nodeDefinitionId = ProcessUtils.findNodeDefinitionIdOrType(node)
    return availableIdsInCategory.includes(nodeDefinitionId)
}

  getIncomingEdges = (nodeId, process) => this.edgesFromProcess(process).filter(e => e.to === nodeId)

  getEdgesForConnectedNodes = (nodeIds, process) => this.edgesFromProcess(process)
    .filter(edge => nodeIds.includes(edge.from) && nodeIds.includes(edge.to))

  getAllGroups = (process) => _.get(process, "properties.additionalFields.groups", [])

  getCollapsedGroups = (process, expandedGroups) => this.getAllGroups(process)
    .filter(g => !_.includes(expandedGroups, g.id))

  getExpandedGroups = (process, expandedGroups) => this.getAllGroups(process)
    .filter(g => _.includes(expandedGroups, g.id))

  edgeType = (allEdges, node, processDefinitionData) => {
    const edgesForNode = this.edgesForNode(node, processDefinitionData)

    if (edgesForNode.canChooseNodes) {
      return edgesForNode.edges[0]
    } else {
      const currentConnectionsTypes = allEdges.filter((edge) => edge.from === node.id).map(e => e.edgeType)
      return edgesForNode.edges.find(et => !currentConnectionsTypes.find(currentType => _.isEqual(currentType, et)))
    }
  }

  createGroup = (process, newGroup) => {
    const groupId = newGroup.join("-")
    return this._update("properties.additionalFields.groups",
                    (groups) => _.concat((groups || []), [{id: groupId, nodes: newGroup}]), process)
  }

  ungroup = (process, groupToDeleteId) => {
    return this._update("properties.additionalFields.groups",
          (groups) => groups.filter(e => !_.isEqual(e.id, groupToDeleteId)), process)
  }

  editGroup = (process, oldGroupId, newGroup) => {
    const groupForState = {id: newGroup.id, nodes: newGroup.ids}
    return this._update("properties.additionalFields.groups",
             (groups) => _.concat((groups.filter(g => g.id !== oldGroupId)), [groupForState]), process)
  }

  updateGroupsAfterNodeIdChange = (process, oldNodeId, newNodeId) => {
    return this._changeGroupNodes(process, (nodes) => nodes.map((n) => n === oldNodeId ? newNodeId : n));
  }

  updateGroupsAfterNodeDelete = (process, idToDelete) => {
    return this._changeGroupNodes(process, (nodes) => nodes.filter((n) => n !== idToDelete));
  }

  edgesForNode = (node, processDefinitionData, forInput) => {
    const nodeObjectTypeDefinition = ProcessUtils.findNodeDefinitionId(node)
    //TODO: when we add more configuration for joins, probably more complex logic will be needed
    const data =  (processDefinitionData.edgesForNodes
      .filter(e => !forInput || e.isForInputDefinition === forInput)
      //here we use == in second comparison, as we sometimes compare null to undefined :|
      .find(e => e.nodeId.type === node.type && e.nodeId.id == nodeObjectTypeDefinition)) || {edges: [null], canChooseNodes: false}
    return data
  }

  edgeLabel = (edge) => {
    const edgeType = _.get(edge, "edgeType.type")

    //TODO: should this map be here??
    const edgeTypeToLabel = {
      FilterFalse: "false",
      FilterTrue: "true",
      SwitchDefault: "default",
      SubprocessOutput: _.get(edge, "edgeType.name"),
      NextSwitch: _.get(edge, "edgeType.condition.expression")
    };
    return edgeTypeToLabel[edgeType] || ""
  }

  //we don't allow multi outputs other than split, filter, switch and no multiple inputs
  //TODO remove type (Source, Sink) comparisons
  canMakeLink = (fromId, toId, process, processDefinitionData) => {
    const nodeInputs = this._nodeInputs(toId, process);
    const nodeOutputs = this._nodeOutputs(fromId, process);

    const to = this.getNodeById(toId, process)
    const from = this.getNodeById(fromId, process)
    return (fromId !== toId) && this._canHaveMoreInputs(to, nodeInputs, processDefinitionData) && this._canHaveMoreOutputs(from, nodeOutputs, processDefinitionData)
  }


  //TODO: this function should already exists in lodash?
  _update = (path, fun, object) => {
    return fp.set(path, fun(_.get(object, path)), object)
  }

  _changeGroupNodes = (processToDisplay, nodeOperation) => {
    return this._update("properties.additionalFields.groups",
      (groups) => (groups || []).map(group => ({
                    ...group,
                    nodes: nodeOperation(group.nodes)
                  })), processToDisplay
    );
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

  _nodeInputs = (nodeId, process) => {
    return this.edgesFromProcess(process).filter(e => e.to == nodeId)
  }

  _nodeOutputs = (nodeId, process) => {
    return this.edgesFromProcess(process).filter(e => e.from == nodeId)
  }

  edgeId = (edge) => {
    return `${edge.from}-${edge.to}`
  }

  //TODO: methods below should be based on backend data, e.g. Subprocess can have outputs or not - based on individual subprocess...
  hasInputs = (node) => (node.type !== "Source" && node.type !== "SubprocessInputDefinition")

  hasOutputs = (node) => (node.type !== "Sink" && node.type !== "SubprocessOutputDefinition")

}

//TODO this pattern is not necessary, just export every public function as in actions.js
export default new NodeUtils()
