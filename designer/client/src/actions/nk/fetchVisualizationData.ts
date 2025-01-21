import { ThunkAction } from "../reduxTypes";
import { displayCurrentProcessVersion, fetchValidatedProcess } from "./process";
import { fetchProcessDefinition } from "./processDefinitionData";
import { loadProcessToolbarsConfiguration } from "./loadProcessToolbarsConfiguration";
import { ProcessName } from "../../components/Process/types";

export function fetchVisualizationData(processName: ProcessName, onSuccess: () => void, onError: (error) => void): ThunkAction {
    return async (dispatch) => {
        try {
            const scenario = await dispatch(displayCurrentProcessVersion(processName));
            const { name, isFragment, processingType } = scenario;
            dispatch(loadProcessToolbarsConfiguration(name));
            dispatch(fetchProcessDefinition(processingType, isFragment)).then((processDefinitionData) =>
                dispatch({ type: "CORRECT_INVALID_SCENARIO", processDefinitionData }),
            );
            dispatch(fetchValidatedProcess(name));
            onSuccess();
            return scenario;
        } catch (error) {
            onError(error);
        }
    };
}
