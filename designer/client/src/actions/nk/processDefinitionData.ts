import HttpService from "../../http/HttpService/instance";
import type { ProcessDefinitionData } from "../../types/scenarioGraph";
import type { ThunkAction } from "../reduxTypes";

export type ProcessDefinitionDataAction = {
    type: "PROCESS_DEFINITION_DATA";
    processDefinitionData: ProcessDefinitionData;
};

export type ProcessingType = string;

export function fetchProcessDefinition(processingType: ProcessingType, isFragment?: boolean): ThunkAction<Promise<ProcessDefinitionData>> {
    return async (dispatch) => {
        const { data } = await HttpService.fetchProcessDefinitionData(processingType, isFragment);

        dispatch({
            type: "PROCESS_DEFINITION_DATA",
            processDefinitionData: data,
        });

        return data;
    };
}
