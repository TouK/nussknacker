/* eslint-disable i18next/no-literal-string */
import { head, isEqual, omit, uniq, uniqWith, values } from "lodash";

import type {
    ExceptionResultJson,
    ExpressionEvaluationResultJson,
    ExternalServiceInvocationResultJson,
    ResultContextJson,
    TestResultsDto,
} from "../http/resultsWithCountsDto";
import type { UIParameter } from "../types/definition";
import type { NodeId } from "../types/node";

export interface TestCapabilities {
    testWithParameters: TestWithParametersCapability;
    testWithLiveData: GenericCapability;
}

export type TestWithParametersCapability = TestWithParametersCapabilityAvailable | TestWithParametersCapabilityNotAvailable;

interface TestWithParametersCapabilityAvailable {
    status: TestCapabilityStatus.AVAILABLE;
    sourceParameters: TestFormParameters[];
}

interface TestWithParametersCapabilityNotAvailable {
    status: TestCapabilityStatus.NOT_AVAILABLE;
    reason: string;
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
    sourceName: string;
    parameters: UIParameter[];
}

export interface NodeTestResults {
    externalServiceInvocationResults: ExternalServiceInvocationResultJson[];
    expressionEvaluationResults: ExpressionEvaluationResultJson[];
    nodeResults: ResultContextJson[];
    errors: ExceptionResultJson[];
}

export interface StateForSelectTestResults {
    testResultsToShow?: NodeResultsForContext;
    testResultsIdToShow?: string;
}

export interface NodeResultsForContext {
    context: ResultContextJson;
    externalInvocationResultsForEveryContext: ExternalServiceInvocationResultJson[];
    expressionResults: Record<string, any>;
    externalInvocationResultsForCurrentContext: ExternalServiceInvocationResultJson[];
    error: string;
}

//TODO move it to backend
class TestResultUtils {
    resultsForNode = (testResults: TestResultsDto, nodeId: NodeId): NodeTestResults | null => {
        const nodeResults = this._nodeResults(testResults, nodeId);
        if (nodeResults) {
            return {
                nodeResults,
                expressionEvaluationResults: this._expressionEvaluationResults(testResults, nodeId),
                externalServiceInvocationResults: this._externalServiceInvocationResults(testResults, nodeId),
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

    private _expressionEvaluationResults(results: TestResultsDto, nodeId: NodeId): ExpressionEvaluationResultJson[] {
        return results?.expressionEvaluationResults?.[nodeId] || [];
    }

    private _externalServiceInvocationResults(results: TestResultsDto, nodeId: NodeId): ExternalServiceInvocationResultJson[] {
        return results?.externalServiceInvocationResults?.[nodeId] || [];
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
            nodeTestResults.expressionEvaluationResults
                .filter((result) => result.contextId == contextId)
                .map((result) => [result.name, result.value]),
        );
        const externalInvocationResultsForCurrentContext = nodeTestResults.externalServiceInvocationResults.filter(
            (result) => result.contextId == contextId,
        );
        const externalInvocationResultsForEveryContext = nodeTestResults.externalServiceInvocationResults;
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
