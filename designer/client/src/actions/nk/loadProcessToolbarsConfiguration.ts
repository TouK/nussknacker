import { getDevToolbars } from "../../components/toolbarSettings/DEV_TOOLBARS";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import { ToolbarsSide } from "../../reducers/toolbars";
import { ThunkAction } from "../reduxTypes";
import HttpService from "../../http/HttpService";

export function loadProcessToolbarsConfiguration(processName: string): ThunkAction {
    return (dispatch, getState) => {
        const state = getState();
        const userSettings = getUserSettings(state);
        HttpService.fetchProcessToolbarsConfiguration(processName).then((response) =>
            dispatch({
                type: "PROCESS_TOOLBARS_CONFIGURATION_LOADED",
                data: {
                    ...response.data,
                    [ToolbarsSide.TopRight]: [{ id: "survey-panel" }, ...response.data.topRight],
                    [ToolbarsSide.BottomRight]: [
                        ...response.data.bottomRight,
                        ...getDevToolbars(userSettings["debug.userSettingsVisible"]),
                    ],
                },
            }),
        );
    };
}
