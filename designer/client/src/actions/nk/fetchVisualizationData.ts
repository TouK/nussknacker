import { ThunkAction } from "../reduxTypes";
import { displayCurrentProcessVersion } from "./process";
import { fetchProcessDefinition } from "./processDefinitionData";
import { loadProcessToolbarsConfiguration } from "./loadProcessToolbarsConfiguration";
import { ProcessName } from "../../components/Process/types";

export function fetchVisualizationData(processName: ProcessName, onSuccess: () => void, onError: (error) => void): ThunkAction {
    return async (dispatch) => {
        try {
            const scenario = await dispatch(displayCurrentProcessVersion(processName));
            const { name, isFragment, processingType } = scenario;
            await dispatch(loadProcessToolbarsConfiguration(name));
            const processDefinitionData = await dispatch(fetchProcessDefinition(processingType, isFragment));
            dispatch({ type: "CORRECT_INVALID_SCENARIO", processDefinitionData });
            onSuccess();
            return scenario;
        } catch (error) {
            onError(error);
        }
    };
}
