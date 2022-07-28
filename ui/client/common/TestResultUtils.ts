/* eslint-disable i18next/no-literal-string */
import {get, head, uniq, values} from "lodash"
import {NodeId} from "../types"

interface Context {
  id: number,
  variables: Record<string, { original?: string }>,
}

interface NodeResults {
  context: Context,
}

interface InvocationResult {
  contextId: Context["id"],
  name: string,
  value: unknown,
}

export interface TestResults {
  mockedResults: { contextId: Context["id"] }[],
  invocationResults: InvocationResult[],
  nodeResults: NodeResults[],
  exceptions: { nodeId: NodeId }[],
  errors: { context: Context, throwable: unknown }[],
}

interface NodeTestResults {
  mockedResults: unknown,
  invocationResults: unknown,
  nodeResults: unknown,
  errors: unknown,
}

export interface StateForSelectTestResults {
  testResultsToShow,
  testResultsIdToShow: number,
}

//TODO move it to backend
class TestResultUtils {

  resultsForNode = (testResults: TestResults, nodeId: NodeId): NodeTestResults | null => {
    if (testResults && this._nodeResults(testResults, nodeId)) {
      return {
        invocationResults: this._invocationResults(testResults, nodeId),
        mockedResults: this._mockedResults(testResults, nodeId),
        nodeResults: this._nodeResults(testResults, nodeId),
        errors: this._errors(testResults, nodeId),
      }
    } else {
      return null
    }
  }

  private _nodeResults = ({nodeResults}: TestResults, nodeId: NodeId) => {
    return (nodeResults || {})[nodeId] || []
  }

  private _invocationResults = ({invocationResults}: TestResults, nodeId: NodeId) => {
    return (invocationResults || {})[nodeId] || []
  }

  private _mockedResults = ({mockedResults}: TestResults, nodeId: NodeId) => {
    return (mockedResults || {})[nodeId] || []
  }

  private _errors = ({exceptions}: TestResults, nodeId: NodeId) => {
    return (exceptions || []).filter((ex) => ex.nodeId == nodeId)
  }

  stateForSelectTestResults = (id: number, testResults: TestResults): StateForSelectTestResults | null => {
    if (this.hasTestResults(testResults)) {
      const chosenId = id || get(head(this.availableContexts(testResults)), "id")
      return {
        testResultsToShow: this.nodeResultsForContext(testResults, chosenId),
        testResultsIdToShow: chosenId,
      }
    } else {
      return null
    }
  }

  availableContexts = (testResults: TestResults) => {
    return uniq(testResults.nodeResults.map(nr => ({id: nr.context.id, display: this._contextDisplay(nr.context)})))

  }

  private _contextDisplay = (context: Context): string => {
    //TODO: what should be here? after aggregate input is not always present :|
    //we assume it's better to display nothing than some crap...
    const varToInclude = context.variables["input"] || head(values(context.variables)) || {}
    return (varToInclude.original || "").toString().substring(0, 50)
  }

  private nodeResultsForContext = (nodeTestResults: TestResults, contextId: number) => {
    const context = (nodeTestResults.nodeResults.find(result => result.context.id == contextId) || {}).context
    const expressionResults = Object.fromEntries(nodeTestResults
      .invocationResults
      .filter(result => result.contextId == contextId)
      .map(result => [result.name, result.value]))
    const mockedResultsForCurrentContext = nodeTestResults.mockedResults.filter(result => result.contextId == contextId)
    const mockedResultsForEveryContext = nodeTestResults.mockedResults
    const error = ((nodeTestResults.errors || []).find((error) => error.context.id === contextId) || {}).throwable
    return {
      context,
      expressionResults,
      mockedResultsForCurrentContext,
      mockedResultsForEveryContext,
      error,
    }
  }

  hasTestResults = (testResults: TestResults): boolean => testResults && this.availableContexts(testResults).length > 0
}

//TODO this pattern is not necessary, just export every public function as in actions.js
export default new TestResultUtils()
