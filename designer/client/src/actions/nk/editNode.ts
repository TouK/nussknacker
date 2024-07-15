import HttpService from "../../http/HttpService";
import { Edge, NodeType, ScenarioGraph, ValidationResult } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { calculateProcessAfterChange } from "./calculateProcessAfterChange";
import { clearProcessCounts } from "./displayProcessCounts";
import { Scenario } from "../../components/Process/types";

export type EditNodeAction = {
    type: "EDIT_NODE";
    before: NodeType;
    after: NodeType;
    validationResult: ValidationResult;
    scenarioGraphAfterChange: ScenarioGraph;
};
export type RenameProcessAction = {
    type: "PROCESS_RENAME";
    name: string;
};

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
