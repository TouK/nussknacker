import HttpService from "../../http/HttpService";
import { ProcessDefinitionData, TypingResult } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { orderBy } from "lodash";

export type ProcessDefinitionDataAction = {
    type: "PROCESS_DEFINITION_DATA";
    processDefinitionData: ProcessDefinitionData;
};

export type ProcessingType = string;

const orderedProcessDefinitionDataClasses = (classes: TypingResult[]) => orderBy(classes, ["display", "refClazzName"]);

export function fetchProcessDefinition(processingType: ProcessingType, isFragment?: boolean): ThunkAction<Promise<ProcessDefinitionData>> {
    return async (dispatch) => {
        const { data: processDefinitionData } = await HttpService.fetchProcessDefinitionData(processingType, isFragment);

        // Since BE doesn't provide ordered classes, to correctly memoize data in redux and not create a new object reference on every state change we need to order classes
        processDefinitionData.classes = orderedProcessDefinitionDataClasses(processDefinitionData.classes);
        dispatch({ type: "PROCESS_DEFINITION_DATA", processDefinitionData });

        return processDefinitionData;
    };
}
