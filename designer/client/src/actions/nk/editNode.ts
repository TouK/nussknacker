import HttpService from "../../http/HttpService";
import { Edge, NodeType, ValidationResult } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { calculateProcessAfterChange } from "./calculateProcessAfterChange";
import { displayProcessCounts } from "./displayProcessCounts";
import { Scenario } from "../../components/Process/types";

export type EditNodeAction = {
    type: "EDIT_NODE";
    before: NodeType;
    after: NodeType;
    validationResult: ValidationResult;
    processAfterChange: $TodoType;
};
export type RenameProcessAction = {
    type: "PROCESS_RENAME";
    name: string;
};

export function editNode(scenarioBefore: Scenario, before: NodeType, after: NodeType, outputEdges?: Edge[]): ThunkAction {
    return async (dispatch) => {
        const scenarioGraph = await dispatch(calculateProcessAfterChange(scenarioBefore, before, after, outputEdges));
        const response = await HttpService.validateProcess({ ...scenarioBefore, json: scenarioGraph });
        dispatch(displayProcessCounts({}));

        return dispatch({
            type: "EDIT_NODE",
            before: before,
            after: after,
            validationResult: response.data,
            processAfterChange: process,
        });
    };
}
