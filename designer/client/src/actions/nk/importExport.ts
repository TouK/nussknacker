import HttpService from "../../http/HttpService";
import { ThunkAction } from "../reduxTypes";
import { ProcessName } from "../../components/Process/types";

export function importFiles(processName: ProcessName, files: File[]): ThunkAction {
    return (dispatch) => {
        files.forEach(async (file: File) => {
            try {
                dispatch({ type: "PROCESS_LOADING" });
                const process = await HttpService.importProcess(processName, file);
                dispatch({ type: "UPDATE_IMPORTED_PROCESS", processJson: process.data });
            } catch (error) {
                dispatch({ type: "LOADING_FAILED" });
            }
        });
    };
}
