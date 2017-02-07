import _ from 'lodash'

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

  nodesFromProcess = (process) => {
    var nodes = process.nodes
    const groups = _.get(process, 'properties.additionalFields.groups', [])
    groups.forEach(group => {
      const groupNodes = nodes.filter(node => _.includes(group, node.id))
      nodes = nodes.filter(node => !_.includes(group, node.id))
      nodes = nodes.concat([{
        id: this._groupId(group),
        type: "_group",
        nodes: groupNodes,
        ids: group
      }])
    })
    return nodes;
  }

  edgesFromProcess = (process) => {
    var edges = process.edges
    const groups = _.get(process, 'properties.additionalFields.groups', [])
    groups.forEach(group => {
      const id = this._groupId(group)
      edges = edges.map(edge => ({
        ...edge,
        from: _.includes(group, edge.from) ? id : edge.from,
        to: _.includes(group, edge.to) ? id : edge.to,
      })).filter(a => !(_.eq(a.from, a.to)))
    })
    return edges;
  }

  _groupId = (group) => {
    return group.join("-")
  }

  edgeType = (allEdges, node, edgesFromDefinition) => {
    if (node.type == "Filter") {
      const thisFilterConnections = allEdges.filter((edge) => edge.from == node.id)
      return {type: _.get(thisFilterConnections[0], 'edgeType.type') == 'FilterTrue' ? 'FilterFalse' : 'FilterTrue'}
    } else if (node.type == "Switch") {
      return edgesFromDefinition["NextSwitch"]
    }
    else {
      return null
    }
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
