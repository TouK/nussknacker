import { useCallback } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { checkPendingChanges } from "../../../actions/nk/checkPendingChanges";
import { loadProcessToolbarsConfiguration } from "../../../actions/nk/loadProcessToolbarsConfiguration";
import { displayCurrentProcessVersion } from "../../../actions/nk/process";
import { getScenarioActivities } from "../../../actions/nk/scenarioActivities";
import type { ThunkAction } from "../../../actions/reduxTypes";
import { visualizationUrl } from "../../../common/VisualizationUrl";
import HttpService from "../../../http/HttpService/instance";
import {
    getProcessName,
    getProcessUnsavedNewName,
    getScenarioGraph,
    getScenarioLabels,
    isProcessRenamed,
} from "../../../reducers/selectors/graph";
import { useAppDispatch } from "../../../store/storeHelpers";

const saveScenario = (comment = ""): ThunkAction<Promise<{ prevName: string; nextName: string }>> => {
    return async (dispatch, getState) => {
        const state = getState();
        const scenarioGraph = getScenarioGraph(state);
        const currentProcessName = getProcessName(state);
        const labels = getScenarioLabels(state);

        // save changes before rename and force same processName everywhere
        await HttpService.saveProcess(currentProcessName, scenarioGraph, comment, labels);

        const unsavedNewName = getProcessUnsavedNewName(state);
        const isRenamed = isProcessRenamed(state) && (await HttpService.changeProcessName(currentProcessName, unsavedNewName));
        const nextProcessName = isRenamed ? unsavedNewName : currentProcessName;

        await dispatch(displayCurrentProcessVersion(nextProcessName));
        await dispatch(await getScenarioActivities(nextProcessName));

        if (isRenamed) {
            await dispatch(loadProcessToolbarsConfiguration(unsavedNewName));
        }

        return { prevName: currentProcessName, nextName: nextProcessName };
    };
};

export const useSaveScenario = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const dispatch = useAppDispatch();

    const switchPath = useCallback(
        ({ prevName, nextName }: { prevName: string; nextName: string }) => {
            if (prevName === nextName) return;
            navigate(
                {
                    ...location,
                    pathname: location.pathname.replace(visualizationUrl(prevName), visualizationUrl(nextName)),
                },
                { replace: true },
            );
        },
        [location, navigate],
    );

    const handleSaveScenarioAction = useCallback(
        async (comment = "") => {
            try {
                await dispatch(checkPendingChanges());
            } catch (error) {
                return;
            }

            const res = await dispatch(saveScenario(comment));
            switchPath(res);
        },
        [dispatch, switchPath],
    );

    return { handleSaveScenarioAction };
};
