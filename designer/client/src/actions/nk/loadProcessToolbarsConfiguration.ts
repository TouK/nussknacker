import HttpService from "../../http/HttpService/instance";
import type { ThunkAction } from "../reduxTypes";

export function loadProcessToolbarsConfiguration(processName: string): ThunkAction {
    return (dispatch) =>
        HttpService.fetchProcessToolbarsConfiguration(processName).then(({ data }) =>
            dispatch({
                type: "PROCESS_TOOLBARS_CONFIGURATION_LOADED",
                data,
            }),
        );
}
