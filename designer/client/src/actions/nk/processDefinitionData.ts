import HttpService from "../../http/HttpService";
import { ProcessDefinitionData } from "../../types";
import { ThunkAction } from "../reduxTypes";

export type ProcessDefinitionDataAction = {
    type: "PROCESS_DEFINITION_DATA";
    processDefinitionData: ProcessDefinitionData;
};

export type ProcessingType = string;

export function fetchProcessDefinition(processingType: ProcessingType, isFragment?: boolean): ThunkAction<Promise<ProcessDefinitionData>> {
    return async (dispatch) => {
        const { data: processDefinitionData } = await HttpService.fetchProcessDefinitionData(processingType, isFragment);

        dispatch({ type: "PROCESS_DEFINITION_DATA", processDefinitionData });

        return processDefinitionData;
    };
}
