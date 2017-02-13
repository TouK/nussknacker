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
  },

  computeBoundingRect: (expandedGroup, layout, nodes, margin) => {

    const widthsHeights = expandedGroup.nodes
      .map(n => {
        const bbox = nodes.find(node => node.id == n).get('size')
        const layoutForNode = ((layout || []).find(k => k.id == n) || {}).position || {}
        return {height: rectHeight, width: bbox.width, x: layoutForNode.x || 0, y: layoutForNode.y || 0}
      })
    const x = _.min(widthsHeights.map(wh => wh.x)) - margin
    const y =  _.min(widthsHeights.map(wh => wh.y)) - margin
    const width = _.max(widthsHeights.map((wh) => wh.x + wh.width - x)) + margin
    const height = _.max(widthsHeights.map((wh) => wh.y + wh.height - y)) + margin
    return { x, y, width, height}

  }

}