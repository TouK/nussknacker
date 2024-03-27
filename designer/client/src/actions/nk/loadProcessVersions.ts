import HttpService from "../../http/HttpService";
import { ScenarioActionName, ProcessName, ProcessVersionType } from "../../components/Process/types";
import { ThunkAction } from "../reduxTypes";

export type LoadProcessVersionsAction = {
    type: "PROCESS_VERSIONS_LOADED";
    history: ProcessVersionType[];
    lastAction: ScenarioActionName;
    lastDeployedAction: ScenarioActionName;
};

export function loadProcessVersions(processName: ProcessName): ThunkAction<Promise<LoadProcessVersionsAction>> {
    return (dispatch) =>
        HttpService.fetchProcessDetails(processName).then((response) => {
            return dispatch({
                type: "PROCESS_VERSIONS_LOADED",
                history: response.data.history,
                lastDeployedAction: response.data.lastDeployedAction,
                lastAction: response.data.lastAction,
            });
        });
}
