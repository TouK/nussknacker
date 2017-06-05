import _ from 'lodash'
import fp from 'lodash/fp'

class NodeUtils {

  nodeType = (node) => {
    return node.type ? node.type : "Properties";
  }

  nodeIsProperties = (node) => {
    const type = node && this.nodeType(node)
    return type == "Properties";
  }

  nodeIsGroup = (node) => {
    return node && this.nodeType(node) == "_group"
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

  getAllGroups = (process) => _.get(process, 'properties.additionalFields.groups', [])

  getCollapsedGroups = (process, expandedGroups) => this.getAllGroups(process)
    .filter(g => !_.includes(expandedGroups, g.id))

  getExpandedGroups = (process, expandedGroups) => this.getAllGroups(process)
    .filter(g => _.includes(expandedGroups, g.id))


  edgeType = (allEdges, node, edgesFromDefinition) => {
    if (node.type == "Filter") {
      const thisFilterConnections = allEdges.filter((edge) => edge.from == node.id)
      return {type: _.get(thisFilterConnections[0], 'edgeType.type') == 'FilterTrue' ? 'FilterFalse' : 'FilterTrue'}
    } else if (node.type == "Switch") {
      return edgesFromDefinition["NextSwitch"]
    } else if (node.type == "SubprocessInput") {
      //FIXME: co tutaj??
      return {type: "SubprocessOutput", name: "output"}
    }
    else {
      return null
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

  edgeLabel = (edge, outgoingEdges) => {
    const edgeTypeToLabel = {
      "FilterFalse": "false",
      "FilterTrue": "true",
      "SwitchDefault": "default",
      "NextSwitch": _.get(edge, 'edgeType.condition.expression')
    }
    const edgeType = _.get(edge, 'edgeType.type')
    return (edgeType == "FilterTrue" && outgoingEdges[edge.from].length == 1 ? '' : edgeTypeToLabel[edgeType]) || ''
  }

  //we don't allow multi outputs other than split, filter, switch and no multiple inputs
  canMakeLink = (from, to, process) => {
    var nodeInputs = this._nodeInputs(to, process);
    var nodeOutputs = this._nodeOutputs(from, process);
    var targetHasNoInput = nodeInputs.length == 0
    var sourceHasNoOutput = nodeOutputs.length == 0
    var canLinkFromSource = sourceHasNoOutput || this._isMultiOutput(from, nodeOutputs, process)
    return targetHasNoInput && canLinkFromSource
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

  _isMultiOutput = (nodeId, nodeOutputs, process) => {
    var node = this._nodeType(nodeId, process)
    return node.type == "Split" || node.type == "Filter" && nodeOutputs.length < 2 || node.type == "Switch"
  }

  _nodeInputs = (nodeId, process) => {
    return this.edgesFromProcess(process).filter(e => e.to == nodeId)
  }

  _nodeOutputs = (nodeId, process) => {
    return this.edgesFromProcess(process).filter(e => e.from == nodeId)
  }

  _nodeType = (nodeId, process) => {
    return this.nodesFromProcess(process).find(n => n.id == nodeId)
  }



}
export default new NodeUtils()
