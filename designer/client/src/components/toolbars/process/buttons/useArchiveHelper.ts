import { useCallback } from "react";
import { useNavigate } from "react-router-dom";

import { loadProcessToolbarsConfiguration } from "../../../../actions/nk/loadProcessToolbarsConfiguration";
import { displayCurrentProcessVersion } from "../../../../actions/nk/process";
import { getScenarioActivities } from "../../../../actions/nk/scenarioActivities";
import { unsavedProcessChanges } from "../../../../common/DialogMessages";
import { ArchivedPath } from "../../../../containers/paths";
import HttpService from "../../../../http/HttpService/instance";
import { isPristine } from "../../../../reducers/selectors/graph";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { flushDraftSave } from "../../../../store/draftAutoSaveListener";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { useWindows } from "../../../../windowManager/useWindows";

export const useArchiveHelper = (processName: string) => {
    const dispatch = useAppDispatch();
    const navigate = useNavigate();
    const { confirm } = useWindows();
    const nothingToSave = useAppSelector(isPristine);
    const draftEnabled = useAppSelector((state) => !!getUserSettings(state)["scenario.enableDraft"]);
    const { redirectAfterArchive } = useAppSelector(getFeatureSettings);

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
        async (archiveConfirmed: boolean) => {
            if (!archiveConfirmed) {
                return;
            }

            if (nothingToSave) {
                return archive();
            }

            if (draftEnabled) {
                flushDraftSave();
                return archive();
            }

            return confirm({
                text: unsavedProcessChanges(),
                onConfirmCallback: async (discardChangesConfirmed) => {
                    if (discardChangesConfirmed) {
                        return archive();
                    }
                },
                confirmText: "DISCARD",
                denyText: "CANCEL",
            });
        },
        [archive, confirm, draftEnabled, nothingToSave],
    );

    return { confirmArchiveCallback };
};
