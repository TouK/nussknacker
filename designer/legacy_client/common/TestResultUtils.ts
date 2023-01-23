/* eslint-disable i18next/no-literal-string */
import {head, uniq, values} from "lodash"
import {NodeId} from "../types"

export interface Context {
  id: string,
  variables: Record<string, { original?: string }>,
}

export interface NodeResult {
  context: Context,
}

export interface InvocationResult {
  contextId: Context["id"],
  name: string,
  value: unknown,
}

export interface Error {
  nodeId: NodeId,
  context: Context,
  throwable,
}

interface ExternalInvocationResult {
  contextId: Context["id"],
}

export interface TestResults {
  externalInvocationResults: Record<NodeId, ExternalInvocationResult[]>,
  invocationResults: Record<NodeId, InvocationResult[]>,
  nodeResults: Record<NodeId, NodeResult[]>,
  exceptions: Error[],
}

export interface NodeTestResults {
  externalInvocationResults: ExternalInvocationResult[],
  invocationResults: InvocationResult[],
  nodeResults: NodeResult[],
  errors: Error[],
}

export interface StateForSelectTestResults {
  testResultsToShow?: NodeResultsForContext,
  testResultsIdToShow?: string,
}

export interface NodeResultsForContext {
  context: Context,
  externalInvocationResultsForEveryContext: ExternalInvocationResult[],
  expressionResults: Record<string, any>,
  externalInvocationResultsForCurrentContext: ExternalInvocationResult[],
  error: Error,
}

//TODO move it to backend
class TestResultUtils {

  resultsForNode = (testResults: TestResults, nodeId: NodeId): NodeTestResults | null => {
    const nodeResults = this._nodeResults(testResults, nodeId)
    if (nodeResults) {
      return {
        nodeResults,
        invocationResults: this._invocationResults(testResults, nodeId),
        externalInvocationResults: this._externalInvocationResults(testResults, nodeId),
        errors: this._errors(testResults, nodeId),
      }
    }
    return null
  }

  stateForSelectTestResults = (testResults?: NodeTestResults, id?: string): StateForSelectTestResults => {
    if (this.hasTestResults(testResults)) {
      const chosenId = id || this.availableContexts(testResults)[0].id
      return {
        testResultsToShow: this.nodeResultsForContext(testResults, chosenId),
        testResultsIdToShow: chosenId,
      }
    }
    return {}
  }

  availableContexts = (testResults: NodeTestResults) => {
    return uniq(testResults.nodeResults.map(nr => ({id: nr.context.id, display: this._contextDisplay(nr.context)})))

  }

  hasTestResults = (testResults?: NodeTestResults): boolean => {
    return testResults && this.availableContexts(testResults).length > 0
  }

  private _nodeResults(results: TestResults, nodeId: NodeId): NodeResult[] {
    return results?.nodeResults?.[nodeId] || []
  }

  private _invocationResults(results: TestResults, nodeId: NodeId): InvocationResult[] {
    return results?.invocationResults?.[nodeId] || []
  }

  private _externalInvocationResults(results: TestResults, nodeId: NodeId): ExternalInvocationResult[] {
    return results?.externalInvocationResults?.[nodeId] || []
  }

  private _errors(results: TestResults, nodeId: NodeId): Error[] {
    return results?.exceptions?.filter((ex) => ex.nodeId === nodeId)
  }

  private _contextDisplay = (context: Context): string => {
    //TODO: what should be here? after aggregate input is not always present :|
    //we assume it's better to display nothing than some crap...
    const varToInclude = context.variables["input"] || head(values(context.variables)) || {}
    return (varToInclude.original || "").toString().substring(0, 50)
  }

  private nodeResultsForContext = (nodeTestResults: NodeTestResults, contextId: string): NodeResultsForContext => {
    const context = nodeTestResults.nodeResults.find(result => result.context.id == contextId)?.context
    const expressionResults = Object.fromEntries(nodeTestResults
      .invocationResults
      .filter(result => result.contextId == contextId)
      .map(result => [result.name, result.value]))
    const externalInvocationResultsForCurrentContext = nodeTestResults.externalInvocationResults.filter(result => result.contextId == contextId)
    const externalInvocationResultsForEveryContext = nodeTestResults.externalInvocationResults
    const error = nodeTestResults.errors?.find((error) => error.context.id === contextId)?.throwable
    return {
      context,
      expressionResults,
      externalInvocationResultsForCurrentContext,
      externalInvocationResultsForEveryContext,
      error,
    }
  }
}

//TODO this pattern is not necessary, just export every public function as in actions.js
export default new TestResultUtils()
