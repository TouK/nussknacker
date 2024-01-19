import { ThunkAction } from "../reduxTypes";
import { displayCurrentProcessVersion } from "./process";
import { displayProcessActivity } from "./displayProcessActivity";
import { fetchProcessDefinition } from "./processDefinitionData";
import { handleHTTPError } from "./errors";
import { loadProcessToolbarsConfiguration } from "./loadProcessToolbarsConfiguration";
import { ProcessName } from "../../components/Process/types";

export function fetchVisualizationData(processName: ProcessName): ThunkAction {
    return async (dispatch) => {
        try {
            const scenario = await dispatch(displayCurrentProcessVersion(processName));
            const { name, isFragment, processingType } = scenario;
            await dispatch(loadProcessToolbarsConfiguration(name));
            dispatch(displayProcessActivity(name));
            const processDefinitionData = await dispatch(fetchProcessDefinition(processingType, isFragment));
            dispatch({ type: "CORRECT_INVALID_SCENARIO", processDefinitionData });
            return scenario;
        } catch (error) {
            dispatch(handleHTTPError(error));
        }
    };
}
