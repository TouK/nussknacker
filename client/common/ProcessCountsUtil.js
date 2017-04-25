import _ from 'lodash'
import NodeUtils from '../components/graph/NodeUtils'
import TestResultUtils from '../common/TestResultUtils'

class ProcessCountsUtil {

  processCountsForTests = (testResults, nodesWithGroups) => {
    const processCountsWithoutGroups =
      _.mapValues(testResults.nodeResults, ((_, nodeId) => {return TestResultUtils.nodeCounts(testResults, nodeId)}))
    return this.processCounts(processCountsWithoutGroups, nodesWithGroups)
  }

  processCounts = (processCountsWithoutGroups, nodesWithGroups) => {
    const nodesGrouped = _.groupBy(nodesWithGroups, 'id')
    const processCountsWithGroups = _.mapValues(nodesGrouped, (nodes) => {return this._processCountsSummary(processCountsWithoutGroups, nodes[0])})
    return {...processCountsWithoutGroups, ...processCountsWithGroups}
  }

  _processCountsSummary = (processCountsWithoutGroups, node) => {
    const ids = NodeUtils.nodeIsGroup(node) ? node.ids : [ node.id ]
    return _.reduce(ids.map(id => processCountsWithoutGroups[id]),
      (acc, n) => ({all: Math.max(n.all, acc.all), errors: Math.max(n.errors, acc.errors)}),
      {all: 0, errors: 0}
    )
  }

}

export default new ProcessCountsUtil()