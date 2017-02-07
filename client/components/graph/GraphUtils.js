import NodeUtils from './NodeUtils'

export default {

  mapProcessWithNewNode: (process, before, after) => {
    return {
      ...process,
      edges: _.map(process.edges, (e) => {
        if (_.isEqual(e.from, before.id)) {
          return {...e, from: after.id, to: e.to}
        } else if (_.isEqual(e.to, before.id)) {
          return {...e, from: e.from, to: after.id}
        } else {
          return e
        }
      }),
      nodes: _.map(process.nodes, (n) => { return _.isEqual(n, before) ? after : n }),
      properties: NodeUtils.nodeIsProperties(before) ? after : process.properties
    }
  },

  mapProcessWithNewEdge: (process, before, after) => {
    return {
      ...process,
      edges: _.map(process.edges, (e) => {
        if (_.isEqual(e.from, before.from) && _.isEqual(e.to, before.to)) {
          return after
        } else {
          return e
        }
      })
    }
  },

  deleteNode: (process, id) => {
    return {
      ...process,
      edges: _.filter(process.edges, (e) => !_.isEqual(e.from, id) && !_.isEqual(e.to, id)),
      nodes: _.filter(process.nodes, (n) => !_.isEqual(n.id, id))
    }
  }

}