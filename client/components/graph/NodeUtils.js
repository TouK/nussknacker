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
}
export default new NodeUtils()
