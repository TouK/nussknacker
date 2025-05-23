import { getNodeResults } from "../../common/ProcessUtilsAsSelectors";
import { getEdgesForNode } from "../../components/graph/node-modal/node/useNodeState";
import { replaceNodeData } from "../../components/graph/node-modal/NodeSwitcherUtils";
import type { Scenario } from "../../components/Process/types";
import HttpService from "../../http/HttpService";
import { updateValidationResult } from "../../reducers/graph";
import { updateAfterNodeDelete } from "../../reducers/graph/utils";
import { getGraph } from "../../reducers/selectors/graph";
import { getProcessDefinitionData } from "../../reducers/selectors/processDefinitionData";
import type { Edge, NodeType, ScenarioGraph, ValidationResult } from "../../types";
import type { ThunkAction } from "../reduxTypes";
import { calculateProcessAfterChange } from "./calculateProcessAfterChange";
import { clearProcessCounts } from "./displayProcessCounts";

export type EditNodeAction = {
    type: "EDIT_NODE";
    before: NodeType;
    after: NodeType;
    validationResult: ValidationResult;
    scenarioGraphAfterChange: ScenarioGraph;
};

export type EditScenarioLabels = {
    type: "EDIT_LABELS";
    labels: string[];
};

export function editScenarioLabels(scenarioLabels: string[]) {
    return (dispatch) => {
        dispatch({ type: "EDIT_LABELS", labels: scenarioLabels });
    };
}

export function editNode(scenarioBefore: Scenario, before: NodeType, after: NodeType, outputEdges?: Edge[]): ThunkAction {
    return async (dispatch, getState) => {
        const scenarioGraph = await dispatch(calculateProcessAfterChange(scenarioBefore, before, after, outputEdges));
        const { data } = await HttpService.validateProcess(scenarioBefore.name, scenarioBefore.name, scenarioGraph);
        const state = getState();
        const currentNodeResults = getNodeResults(state);
        const validationResult = updateValidationResult(currentNodeResults, data);

        dispatch(clearProcessCounts());
        dispatch({
            type: "EDIT_NODE",
            before,
            after,
            validationResult,
            scenarioGraphAfterChange: scenarioGraph,
        });
    };
}

export function replaceNode(before: NodeType, after: NodeType): ThunkAction {
    return async (dispatch, getState) => {
        const state = getState();
        const graph = getGraph(state);
        const { scenario } = before.id === after.id ? graph : updateAfterNodeDelete(graph, after.id);
        const { nextEdges: outputEdges, nextNode } = replaceNodeData(
            before,
            after,
            getProcessDefinitionData(state),
            getEdgesForNode(scenario, before),
        );

        dispatch(editNode(scenario, before, nextNode, outputEdges));
    };
}
