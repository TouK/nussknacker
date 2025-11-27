/* eslint-disable i18next/no-literal-string */
import { head, isEqual, omit, uniq, uniqWith, values } from "lodash";

import type {
    ExceptionResultJson,
    ExpressionInvocationResultJson,
    ExternalInvocationResultJson,
    ResultContextJson,
    TestResultsDto,
} from "../http/resultsWithCountsDto";
import type { NodeId, UIParameter } from "../types";

export interface TestCapabilities {
    testWithParameters: TestWithParametersCapability;
    testWithLiveData: GenericCapability;
}

export interface TestWithParametersCapability {
    status: TestCapabilityStatus;
    sourceParameters?: TestFormParameters[];
}

export interface GenericCapability {
    status: TestCapabilityStatus;
}

export enum TestCapabilityStatus {
    AVAILABLE = "AVAILABLE",
    NOT_AVAILABLE = "NOT_AVAILABLE",
}

export interface TestFormParameters {
    sourceId: string;
    parameters: UIParameter[];
}

export interface NodeTestResults {
    externalInvocationResults: ExternalInvocationResultJson[];
    invocationResults: ExpressionInvocationResultJson[];
    nodeResults: ResultContextJson[];
    errors: ExceptionResultJson[];
}

export interface StateForSelectTestResults {
    testResultsToShow?: NodeResultsForContext;
    testResultsIdToShow?: string;
}

export interface NodeResultsForContext {
    context: ResultContextJson;
    externalInvocationResultsForEveryContext: ExternalInvocationResultJson[];
    expressionResults: Record<string, any>;
    externalInvocationResultsForCurrentContext: ExternalInvocationResultJson[];
    error: string;
}

//TODO move it to backend
class TestResultUtils {
    resultsForNode = (testResults: TestResultsDto, nodeId: NodeId): NodeTestResults | null => {
        const nodeResults = this._nodeResults(testResults, nodeId);
        if (nodeResults) {
            return {
                nodeResults,
                invocationResults: this._invocationResults(testResults, nodeId),
                externalInvocationResults: this._externalInvocationResults(testResults, nodeId),
                errors: this._errors(testResults, nodeId),
            };
        }
        return null;
    };

    errorsForNode = (testResults: TestResultsDto, nodeId: NodeId): ExceptionResultJson[] => {
        return this._errors(testResults, nodeId);
    };

    stateForSelectTestResults = (testResults?: NodeTestResults, id?: string): StateForSelectTestResults => {
        if (this.hasTestResults(testResults)) {
            return {
                testResultsToShow: this.nodeResultsForContext(testResults, id),
                testResultsIdToShow: id,
            };
        }
        return {};
    };

    availableContexts = (testResults: NodeTestResults) => {
        return uniq(testResults.nodeResults.map((nr) => ({ id: nr.id, display: this._contextDisplay(nr) })));
    };

    hasTestResults = (testResults?: NodeTestResults): boolean => {
        return testResults && this.availableContexts(testResults).length > 0;
    };

    private _nodeResults(results: TestResultsDto, nodeId: NodeId): ResultContextJson[] {
        const allNodesTransitionResults = results?.nodeTransitionResults || [];
        const inboundTransitions = allNodesTransitionResults.filter((r) => r.destinationNodeId === nodeId);
        const outboundTransitions = allNodesTransitionResults.filter((r) => r.sourceNodeId === nodeId);
        const transitions = inboundTransitions.length != 0 ? inboundTransitions : outboundTransitions;
        const resultsFromTransitions = transitions.flatMap(({ results }) => results);
        // After a fragment usage node, two outgoing transitions are produced:
        // 1) one from the direct output usage (with test results for fragment usage)
        // 2) one from fragment output (with test results for fragment output)
        // For the next node, these became inbound transitions that are identical except for the timestamp field, so we remove such duplicates here
        return uniqWith(resultsFromTransitions, (a, b) => isEqual(omit(a, "timestamp"), omit(b, "timestamp")));
    }

    private _invocationResults(results: TestResultsDto, nodeId: NodeId): ExpressionInvocationResultJson[] {
        return results?.invocationResults?.[nodeId] || [];
    }

    private _externalInvocationResults(results: TestResultsDto, nodeId: NodeId): ExternalInvocationResultJson[] {
        return results?.externalInvocationResults?.[nodeId] || [];
    }

    private _errors(results: TestResultsDto, nodeId: NodeId): ExceptionResultJson[] {
        return results?.exceptionsByNodeId[nodeId] ?? [];
    }

    private _contextDisplay = (context: ResultContextJson): string => {
        //TODO: what should be here? after aggregate input is not always present :|
        //we assume it's better to display nothing than some crap...
        const { original = "" } = context.variables["input"] || head(values(context.variables)) || {};
        return original.toString().substring(0, 50);
    };

    private nodeResultsForContext = (nodeTestResults: NodeTestResults, contextId: string): NodeResultsForContext => {
        const context = nodeTestResults.nodeResults.find((result) => result.id == contextId);
        const expressionResults = Object.fromEntries(
            nodeTestResults.invocationResults
                .filter((result) => result.contextId == contextId)
                .map((result) => [result.name, result.value]),
        );
        const externalInvocationResultsForCurrentContext = nodeTestResults.externalInvocationResults.filter(
            (result) => result.contextId == contextId,
        );
        const externalInvocationResultsForEveryContext = nodeTestResults.externalInvocationResults;
        const error = nodeTestResults.errors?.find((error) => error.context.id === contextId)?.throwable;
        return {
            context,
            expressionResults,
            externalInvocationResultsForCurrentContext,
            externalInvocationResultsForEveryContext,
            error,
        };
    };
}

//TODO this pattern is not necessary, just export every public function as in actions.js
export default new TestResultUtils();
