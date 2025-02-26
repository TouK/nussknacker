import HttpService from "../../http/HttpService";
import { appendAdditionalCreators } from "../../reducers/selectors/getComponentGroups";
import { getAdditionalComponents } from "../../reducers/selectors/isCloudInstance";
import { ProcessDefinitionData } from "../../types";
import { ThunkAction } from "../reduxTypes";

export type ProcessDefinitionDataAction = {
    type: "PROCESS_DEFINITION_DATA";
    processDefinitionData: ProcessDefinitionData;
};

export type ProcessingType = string;

export function fetchProcessDefinition(processingType: ProcessingType, isFragment?: boolean): ThunkAction<Promise<ProcessDefinitionData>> {
    return async (dispatch, getState) => {
        const { data } = await HttpService.fetchProcessDefinitionData(processingType, isFragment);
        const state = getState();

        dispatch({
            type: "PROCESS_DEFINITION_DATA",
            processDefinitionData: {
                ...data,
                componentGroups: appendAdditionalCreators(data.componentGroups, getAdditionalComponents(state)),
            },
        });

        return data;
    };
}
