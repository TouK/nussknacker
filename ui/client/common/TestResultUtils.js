import _ from 'lodash'

//TODO move it to backend
class TestResultUtils {


  resultsForNode = (testResults, nodeId) => {
    if (testResults && this._nodeResults(testResults, nodeId)) {
      return {
        invocationResults: this._invocationResults(testResults, nodeId),
        mockedResults: this._mockedResults(testResults, nodeId),
        nodeResults: this._nodeResults(testResults, nodeId),
        errors: this._errors(testResults, nodeId)
      }
    } else {
      return null;
    }
  }

  _nodeResults = (testResults, nodeId) => {
    return (testResults.nodeResults || {})[nodeId] || []
  }

  _invocationResults = (testResults, nodeId) => {
    return (testResults.invocationResults || {})[nodeId] || []
  }

  _mockedResults = (testResults, nodeId) => {
    return (testResults.mockedResults || {})[nodeId] || []
  }

  _errors = (testResults, nodeId) => {
    return (testResults.exceptions || []).filter((ex) => ex.nodeId == nodeId)
  }

  nodeResultsForContext = (nodeTestResults, contextId) => {
    var context = (nodeTestResults.nodeResults.find(result => result.context.id == contextId) || {}).context
    var expressionResults = _.fromPairs(nodeTestResults
      .invocationResults
      .filter(result => result.contextId == contextId)
      .map(result => [result.name, result.value]))
    var mockedResultsForCurrentContext = nodeTestResults.mockedResults.filter(result => result.contextId == contextId)
    var mockedResultsForEveryContext = nodeTestResults.mockedResults
    var error = ((nodeTestResults.errors || []).find((error) => error.context.id == contextId) || {}).exception
    return {
      context: context,
      expressionResults: expressionResults,
      mockedResultsForCurrentContext: mockedResultsForCurrentContext,
      mockedResultsForEveryContext: mockedResultsForEveryContext,
      error: error
    }
  }

  availableContexts = (testResults) => {
    return _.uniq(testResults.nodeResults.map(nr => ({id: nr.context.id, display: this._contextDisplay(nr.context)})))

  }

  _contextDisplay = (context) => {
    //TODO: what should be here? after aggregate input is not always present :|
    //we assume it's better to display nothing than some crap...
    const varToInclude = context.variables["input"] || _.head(_.values(context.variables)) || {};
    return (varToInclude.original || "").toString().substring(0, 50)
  }
}
//TODO this pattern is not necessary, just export every public function as in actions.js
export default new TestResultUtils()
