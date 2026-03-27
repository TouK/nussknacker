import type { ProcessName } from "../../components/Process/types";
import HttpService from "../../http/HttpService/instance";
import type { ThunkAction } from "../reduxTypes";

export function importFiles(processName: ProcessName, files: File[]): ThunkAction {
    return (dispatch) => {
        files.forEach(async (file: File) => {
            try {
                dispatch({ type: "PROCESS_LOADING" });
                const importedScenario = await HttpService.importScenario(processName, file);
                dispatch({ type: "UPDATE_IMPORTED_PROCESS", importedScenarioData: importedScenario.data });
            } catch (error) {
                dispatch({ type: "LOADING_FAILED" });
            }
        });
    };
}
