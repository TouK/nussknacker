import { ThunkAction } from "../reduxTypes";
import HttpService from "../../http/HttpService";

export function loadProcessToolbarsConfiguration(processName: string): ThunkAction {
    return (dispatch) =>
        HttpService.fetchProcessToolbarsConfiguration(processName).then((response) =>
            dispatch({
                type: "PROCESS_TOOLBARS_CONFIGURATION_LOADED",
                data: response.data,
            }),
        );
}
