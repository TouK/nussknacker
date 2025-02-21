import { ThunkAction } from "../reduxTypes";
import { displayTestCapabilities, fetchStickyNotesForScenario } from "./process";
import { fetchProcessDefinition } from "./processDefinitionData";
import { loadProcessToolbarsConfiguration } from "./loadProcessToolbarsConfiguration";
import { ProcessName } from "../../components/Process/types";
import HttpService from "../../http/HttpService";

// This function is responsible for the initial fetching of scenario visualization
// 1. Fetch (blocking, with await) latest scenario, but without validation, which makes it very quick.
// 2. Fetch (blocking, with await) process definition data for the processing type.
// 3. After requests 1 and 2 are made, and graph began loading in the browser, then simultaneously and non-blocking:
//    - fetch scenario validation data
//    - fetch toolbars configuration
//    - fetch test capabilities
//    - fetch sticky notes
// IMPORTANT: The initial fetch of the scenario graph is performed with flag `skipValidateAndResolve=true`.
//            There are 2 effects of that:
//            - there is no validation result in the response (it is fetched later, asynchronously)
//            - the `dictKeyWithLabel` expressions may not be fully resolved (missing label, which needs to be fetched separately, see `DictParameterEditor`)
export function fetchVisualizationData(processName: ProcessName, onSuccess: () => void, onError: (error) => void): ThunkAction {
    return async (dispatch) => {
        try {
            dispatch({ type: "PROCESS_FETCH" });
            const response = await HttpService.fetchLatestProcessDetailsWithoutValidation(processName);
            const scenario = response.data;
            const { name, isFragment, processingType } = scenario;
            await dispatch(fetchProcessDefinition(processingType, isFragment)).then((processDefinitionData) => {
                dispatch({ type: "DISPLAY_PROCESS", scenario: scenario });
                dispatch({ type: "CORRECT_INVALID_SCENARIO", processDefinitionData });
            });
            dispatch(loadProcessToolbarsConfiguration(name));
            dispatch(displayTestCapabilities(name, scenario.scenarioGraph));
            dispatch(fetchStickyNotesForScenario(name, scenario.processVersionId));
            HttpService.validateProcess(name, name, scenario.scenarioGraph).then(({ data }) =>
                dispatch({ type: "VALIDATION_RESULT", validationResult: data }),
            );
            onSuccess();
            return scenario;
        } catch (error) {
            onError(error);
        }
    };
}
