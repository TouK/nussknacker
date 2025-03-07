import { getEdgesForNode } from "../../components/graph/node-modal/node/NodeDetails";
import { replaceNodeData } from "../../components/graph/node-modal/NodeSwitcher";
import { Scenario } from "../../components/Process/types";
import HttpService from "../../http/HttpService";
import { updateAfterNodeDelete } from "../../reducers/graph/utils";
import { getGraph } from "../../reducers/selectors/graph";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { Edge, NodeType, ScenarioGraph, ValidationResult } from "../../types";
import { ThunkAction } from "../reduxTypes";
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
    return async (dispatch) => {
        const { processName, scenarioGraph } = await dispatch(calculateProcessAfterChange(scenarioBefore, before, after, outputEdges));
        const response = await HttpService.validateProcess(scenarioBefore.name, processName, scenarioGraph);

        dispatch(clearProcessCounts());
        dispatch({
            type: "EDIT_NODE",
            before,
            after,
            validationResult: response.data,
            scenarioGraphAfterChange: scenarioGraph,
        });
    };
}

export function replaceNode(before: NodeType, after: NodeType): ThunkAction {
    return async (dispatch, getState) => {
        const state = getState();
        const graph = getGraph(state);
        let scenario: Scenario;
        if (before.id !== after.id) {
            ({ scenario } = updateAfterNodeDelete(graph, after.id));
        } else {
            ({ scenario } = graph);
        }
        const { nextEdges: outputEdges, nextNode } = replaceNodeData(
            before,
            after,
            getProcessDefinitionData(state),
            getEdgesForNode(scenario, before),
        );

        dispatch(editNode(scenario, before, nextNode, outputEdges));
    };
}
