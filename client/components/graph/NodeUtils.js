import _ from 'lodash'
import fp from 'lodash/fp'
import ProcessUtils from '../../common/ProcessUtils.js'

class NodeUtils {

  nodeType = (node) => {
    return node.type ? node.type : "Properties";
  }

  nodeIsProperties = (node) => {
    const type = node && this.nodeType(node)
    return type === "Properties";
  }

  nodeIsGroup = (node) => {
    return node && this.nodeType(node) === "_group"
  }

  nodesFromProcess = (process, expandedGroups) => {
    var nodes = process.nodes
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
    var edges = process.edges
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


  getAllGroups = (process) => _.get(process, 'properties.additionalFields.groups', [])

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
      return edgesForNode.edges.find(et => !currentConnectionsTypes.includes(et))
    }
  }

  createGroup = (process, newGroup) => {
    const groupId = newGroup.join("-")
    return this._update('properties.additionalFields.groups',
                    (groups) => _.concat((groups || []), [{id: groupId, nodes: newGroup}]), process)
  }

  ungroup = (process, groupToDeleteId) => {
    return this._update('properties.additionalFields.groups',
          (groups) => groups.filter(e => !_.isEqual(e.id, groupToDeleteId)), process)
  }

  editGroup = (process, oldGroupId, newGroup) => {
    const groupForState = {id: newGroup.id, nodes: newGroup.ids}
    return this._update('properties.additionalFields.groups',
             (groups) => _.concat((groups.filter(g => g.id !== oldGroupId)), [groupForState]), process)
  }

  updateGroupsAfterNodeIdChange = (process, oldNodeId, newNodeId) => {
    return this._changeGroupNodes(process, (nodes) => nodes.map((n) => n === oldNodeId ? newNodeId : n));
  }

  updateGroupsAfterNodeDelete = (process, idToDelete) => {
    return this._changeGroupNodes(process, (nodes) => nodes.filter((n) => n !== idToDelete));
  }

  edgesForNode = (node, processDefinitionData) => {
    const nodeObjectTypeDefinition = ProcessUtils.findNodeDefinitionId(node)
    return processDefinitionData.edgesForNodes
      //here we use == in second comparison, as we sometimes compare null to undefined :|
      .find(e => e.nodeId.type === node.type && e.nodeId.id == nodeObjectTypeDefinition) || { edges: [null], canChooseNodes: false}
  }

  edgeLabel = (edge, outgoingEdges) => {
    const edgeType = _.get(edge, 'edgeType.type')
    const isSingleOutput = outgoingEdges && outgoingEdges[edge.from].length === 1

    //TODO: should this map be here??
    const edgeTypeToLabel = {
      "FilterFalse": "false",
      "FilterTrue": isSingleOutput ? '' : "true",
      "SwitchDefault": "default",
      "SubprocessOutput": _.get(edge, 'edgeType.name'),
      "NextSwitch": _.get(edge, 'edgeType.condition.expression')
    };
    return edgeTypeToLabel[edgeType] || ''
  }

  //we don't allow multi outputs other than split, filter, switch and no multiple inputs
  canMakeLink = (fromId, to, process, processDefinitionData) => {
    const nodeInputs = this._nodeInputs(to, process);
    const nodeOutputs = this._nodeOutputs(fromId, process);

    const targetHasNoInput = nodeInputs.length === 0
    const from = this.getNodeById(fromId, process)
    return targetHasNoInput && this._canHaveMoreOutputs(from, nodeOutputs, processDefinitionData)
  }


  //TODO: no przeciez to juz powinno byc??
  _update = (path, fun, object) => {
    return fp.set(path, fun(_.get(object, path)), object)
  }

  _changeGroupNodes = (processToDisplay, nodeOperation) => {
    return this._update('properties.additionalFields.groups',
      (groups) => (groups || []).map(group => ({
                    ...group,
                    nodes: nodeOperation(group.nodes)
                  })), processToDisplay
    );
  }

  _canHaveMoreOutputs = (node, nodeOutputs, processDefinitionData) => {
    const edgesForNode = this.edgesForNode(node, processDefinitionData)
    const maxEdgesForNode = edgesForNode.edges.length
    return edgesForNode.canChooseNodes || nodeOutputs.length < maxEdgesForNode
  }

  _nodeInputs = (nodeId, process) => {
    return this.edgesFromProcess(process).filter(e => e.to == nodeId)
  }

  _nodeOutputs = (nodeId, process) => {
    return this.edgesFromProcess(process).filter(e => e.from == nodeId)
  }



}
export default new NodeUtils()
