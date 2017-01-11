import _ from 'lodash'

class TestResultUtils {


  _nodeResults = (testResults, nodeId) => {
    return (testResults.nodeResults || {})[nodeId] || []
  }

  _invocationResults = (testResults, nodeId) => {
    return (testResults.invocationResults || {})[nodeId] || []
  }

  _errors = (testResults, nodeId) => {
    return (testResults.exceptions || []).filter((ex) => ex.nodeId == nodeId)
  }

  resultsForNode = (testResults, nodeId) => {
    if (testResults && this._nodeResults(testResults, nodeId)) {
      return {
        invocationResults: this._invocationResults(testResults, nodeId),
        nodeResults: this._nodeResults(testResults, nodeId),
        errors: this._errors(testResults, nodeId)
      }
    } else {
      return null;
    }
  }

  nodeResultsForContext = (nodeTestResults, contextId) => {
    var context = (nodeTestResults.nodeResults.find(result => result.context.id == contextId) || {}).context
    var expressionResults = _.fromPairs(nodeTestResults
      .invocationResults
      .filter(result => result.context.id == contextId)
      .map(result => [result.name, result.value]))
    var error = ((nodeTestResults.errors || []).find((error) => error.context.id == contextId) || {}).exception
    return {
      context: context,
      expressionResults: expressionResults,
      error: error
    }
  }

  availableContexts = (testResults) => {
    var varToInclude = "input"
    return _.uniq(testResults.nodeResults.map(nr => ({id: nr.context.id, [varToInclude]: nr.context.variables[varToInclude]})))

  }
}

export default new TestResultUtils()
