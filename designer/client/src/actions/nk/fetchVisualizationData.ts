import { ThunkAction } from "../reduxTypes";
import { displayCurrentProcessVersion } from "./process";
import { displayProcessActivity } from "./displayProcessActivity";
import { fetchProcessDefinition } from "./processDefinitionData";
import { handleHTTPError } from "./errors";
import { loadProcessToolbarsConfiguration } from "./loadProcessToolbarsConfiguration";
import { ProcessId } from "../../types";

export function fetchVisualizationData(processName: ProcessId): ThunkAction {
    return async (dispatch) => {
        try {
            const fetchedProcessDetails = await dispatch(displayCurrentProcessVersion(processName));
            const { name, json, processingType } = fetchedProcessDetails;
            await dispatch(loadProcessToolbarsConfiguration(name));
            dispatch(displayProcessActivity(name));
            const processDefinitionData = await dispatch(fetchProcessDefinition(processingType, json.properties?.isFragment));
            dispatch({ type: "CORRECT_INVALID_SCENARIO", processDefinitionData });
            return fetchedProcessDetails;
        } catch (error) {
            dispatch(handleHTTPError(error));
        }
    };
}
