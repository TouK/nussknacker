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
    const context = (nodeTestResults.nodeResults.find(result => result.context.id == contextId) || {}).context
    const expressionResults = _.fromPairs(nodeTestResults
      .invocationResults
      .filter(result => result.contextId == contextId)
      .map(result => [result.name, result.value]))
    const mockedResultsForCurrentContext = nodeTestResults.mockedResults.filter(result => result.contextId == contextId)
    const mockedResultsForEveryContext = nodeTestResults.mockedResults
    const error = ((nodeTestResults.errors || []).find((error) => error.context.id === contextId) || {}).throwable
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

  stateForSelectTestResults = (id, testResults) => {
    if (this.hasTestResults(testResults)) {
      const chosenId = id || _.get(_.head(this.availableContexts(testResults)), "id")
      return {
        testResultsToShow: this.nodeResultsForContext(testResults, chosenId),
        testResultsIdToShow: chosenId
      }
    } else {
      return null
    }
  }

  hasTestResults = (testResults) => testResults && this.availableContexts(testResults).length > 0
}
//TODO this pattern is not necessary, just export every public function as in actions.js
export default new TestResultUtils()
