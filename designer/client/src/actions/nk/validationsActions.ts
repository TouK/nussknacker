import { getProcessName, getProcessProperties } from "../../components/graph/node-modal/NodeDetailsContent/selectors";
import { applyIdFromFakeName } from "../../components/graph/node-modal/nodeIdFieldHelpers";
import { appendNodeDataToProperties, cleanProperties, isRequestSource } from "../../components/graph/node-modal/requestSourceAddons";
import HttpService from "../../http/HttpService/instance";
import type { NodeType, PropertiesType } from "../../types/node";
import type { NodeValidationError } from "../../types/validation";
import type { ThunkAction } from "../reduxTypes";
import type { ValidationData, ValidationRequest } from "./nodeDetails";
import { nodeValidationDataUpdated } from "./nodeDetails";

export type ValidationsActions =
    | { type: "PROPERTIES_VALIDATION_UPDATED"; properties: PropertiesType; errors: NodeValidationError[] }
    | { type: "VALIDATE_NODE"; node: NodeType }
    | { type: "VALIDATE_PROPERTIES"; properties: PropertiesType };

export function validateScenarioProperties({ name, additionalFields }: PropertiesType): ThunkAction {
    return async (dispatch, getState) => {
        dispatch({ type: "VALIDATE_PROPERTIES", properties: { name, additionalFields } });

        const scenarioName = getProcessName(getState());
        const data = await HttpService.validateProperties(scenarioName, { additionalFields, name });
        if (!data) return;

        dispatch({ type: "PROPERTIES_VALIDATION_UPDATED", properties: { name, additionalFields }, errors: data.validationErrors });
    };
}

export function validateNode({
    nodeData,
    ...validationRequestData
}: Omit<ValidationRequest, "processProperties">): ThunkAction<Promise<ValidationData | void>> {
    return async (dispatch, getState) => {
        dispatch({ type: "VALIDATE_NODE", node: nodeData });
        const processProperties = getProcessProperties(getState());

        if (isRequestSource(nodeData)) {
            await dispatch(validateScenarioProperties(appendNodeDataToProperties(processProperties, nodeData)));
            nodeData = cleanProperties(nodeData);
        }

        const scenarioName = getProcessName(getState());

        const data = await HttpService.validateNode(scenarioName, {
            ...validationRequestData,
            nodeData: applyIdFromFakeName(nodeData),
            processProperties,
        });

        if (!data) return;

        // node details view creates this on open and removes after close
        dispatch(nodeValidationDataUpdated(nodeData.id, data));

        return data;
    };
}
