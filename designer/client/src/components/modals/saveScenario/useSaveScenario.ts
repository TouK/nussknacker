import { useCallback } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { displayCurrentProcessVersion, loadProcessToolbarsConfiguration } from "../../../actions/nk";
import { getScenarioActivities } from "../../../actions/nk/scenarioActivities";
import type { ThunkAction } from "../../../actions/reduxTypes";
import { visualizationUrl } from "../../../common/VisualizationUrl";
import HttpService from "../../../http/HttpService";
import {
    getProcessName,
    getProcessUnsavedNewName,
    getScenarioGraph,
    getScenarioLabels,
    isProcessRenamed,
} from "../../../reducers/selectors/graph";
import { useAppDispatch } from "../../../store/storeHelpers";

export const useSaveScenario = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const dispatch = useAppDispatch();

    const saveScenario = useCallback(
        (comment = ""): ThunkAction => {
            return async (dispatch, getState) => {
                const state = getState();
                const scenarioGraph = getScenarioGraph(state);
                const currentProcessName = getProcessName(state);
                const labels = getScenarioLabels(state);

                // save changes before rename and force same processName everywhere
                await HttpService.saveProcess(currentProcessName, scenarioGraph, comment, labels);

                const unsavedNewName = getProcessUnsavedNewName(state);
                const isRenamed = isProcessRenamed(state) && (await HttpService.changeProcessName(currentProcessName, unsavedNewName));
                const processName = isRenamed ? unsavedNewName : currentProcessName;

                await dispatch(displayCurrentProcessVersion(processName));
                await dispatch(await getScenarioActivities(processName));

                if (isRenamed) {
                    await dispatch(loadProcessToolbarsConfiguration(unsavedNewName));
                    navigate(
                        {
                            ...location,
                            pathname: location.pathname.replace(visualizationUrl(currentProcessName), visualizationUrl(unsavedNewName)),
                        },
                        { replace: true },
                    );
                }
            };
        },
        [location, navigate],
    );

    const handleSaveScenarioAction = useCallback(
        async (comment = "") => {
            await dispatch(saveScenario(comment));
        },
        [dispatch, saveScenario],
    );

    return { handleSaveScenarioAction };
};
