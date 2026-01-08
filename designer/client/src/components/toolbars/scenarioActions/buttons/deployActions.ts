import { checkPendingChanges } from "../../../../actions/nk/checkPendingChanges";
import { fetchProcessToDisplay, loadProcessState } from "../../../../actions/nk/process";
import type { ThunkAction } from "../../../../actions/reduxTypes";
import HttpService from "../../../../http/HttpService/instance";
import type { NodesDeploymentData } from "../../../../http/HttpService/types";
import { getProcessName, getProcessVersionId, getScenarioGraphSource } from "../../../../reducers/selectors/graph";
import type { ScenarioActionResult } from "./types";
import { ScenarioActionResultType } from "./types";

const createAction =
    (type: "deploy" | "redeploy") =>
    (comment = "", nodesDeploymentData: NodesDeploymentData = null): ThunkAction<Promise<ScenarioActionResult>> =>
    async (dispatch, getState) => {
        try {
            await dispatch(checkPendingChanges());
        } catch (error) {
            const result: ScenarioActionResult = {
                msg: error,
                scenarioActionResultType: ScenarioActionResultType.UnhandledError,
            };
            return result;
        }

        const state = getState();
        const scenarioGraphSource = getScenarioGraphSource(state);
        const processVersionId = getProcessVersionId(state);
        const name = getProcessName(state);

        const result = await HttpService[type](name, comment, nodesDeploymentData, scenarioGraphSource);
        if (result.scenarioActionResultType === ScenarioActionResultType.DeploySuccess) {
            dispatch(fetchProcessToDisplay(name, result.deployedScenarioVersionId));
        } else {
            dispatch(loadProcessState(name, processVersionId));
        }
        return result;
    };

export const redeployAction = createAction("redeploy");
export const deployAction = createAction("deploy");
