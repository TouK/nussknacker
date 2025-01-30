import { ThunkAction } from "../reduxTypes";
import { displayTestCapabilities, fetchStickyNotesForScenario, fetchValidatedProcess } from "./process";
import { fetchProcessDefinition } from "./processDefinitionData";
import { loadProcessToolbarsConfiguration } from "./loadProcessToolbarsConfiguration";
import { ProcessName } from "../../components/Process/types";
import HttpService from "../../http/HttpService";

export function fetchVisualizationData(processName: ProcessName, onSuccess: () => void, onError: (error) => void): ThunkAction {
    return async (dispatch) => {
        try {
            dispatch({ type: "PROCESS_FETCH" });
            const response = await HttpService.fetchLatestProcessDetailsWithoutValidation(processName);
            const scenario = response.data;
            const { name, isFragment, processingType } = scenario;
            await dispatch(fetchProcessDefinition(processingType, isFragment)).then((processDefinitionData) => {
                dispatch({ type: "DISPLAY_PROCESS", scenario: response.data });
                dispatch({ type: "CORRECT_INVALID_SCENARIO", processDefinitionData });
            });
            dispatch(loadProcessToolbarsConfiguration(processName));
            dispatch(displayTestCapabilities(processName, response.data.scenarioGraph));
            dispatch(fetchStickyNotesForScenario(processName, response.data.processVersionId));
            dispatch(fetchValidatedProcess(name));
            onSuccess();
            return scenario;
        } catch (error) {
            onError(error);
        }
    };
}
