import { useCallback } from "react";
import { useNavigate } from "react-router-dom";

import { loadProcessToolbarsConfiguration } from "../../../../actions/nk/loadProcessToolbarsConfiguration";
import { displayCurrentProcessVersion } from "../../../../actions/nk/process";
import { getScenarioActivities } from "../../../../actions/nk/scenarioActivities";
import { ArchivedPath } from "../../../../containers/paths";
import HttpService from "../../../../http/HttpService/instance";
import { isPristine } from "../../../../reducers/selectors/graph";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { useUnsavedChangesPrompt } from "../../../useUnsavedChangesPrompt";

export const useArchiveHelper = (processName: string) => {
    const dispatch = useAppDispatch();
    const navigate = useNavigate();
    const nothingToSave = useAppSelector(isPristine);
    const { redirectAfterArchive } = useAppSelector(getFeatureSettings);
    const { promptOrProceed } = useUnsavedChangesPrompt();

    const archive = useCallback(async () => {
        return HttpService.archiveProcess(processName).then(async () => {
            dispatch({ type: "ARCHIVED" });
            if (redirectAfterArchive) {
                navigate(ArchivedPath);
            } else {
                dispatch(loadProcessToolbarsConfiguration(processName));
                dispatch(displayCurrentProcessVersion(processName));
                await dispatch(await getScenarioActivities(processName));
            }
        });
    }, [dispatch, navigate, processName, redirectAfterArchive]);

    const confirmArchiveCallback = useCallback(
        (archiveConfirmed: boolean) => {
            if (!archiveConfirmed) return;
            if (nothingToSave) {
                archive();
                return;
            }
            promptOrProceed(() => {
                archive();
            });
        },
        [archive, nothingToSave, promptOrProceed],
    );

    return { confirmArchiveCallback };
};
