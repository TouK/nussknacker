import { useNavigate } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { ArchivedPath } from "../../../../containers/paths";
import HttpService from "../../../../http/HttpService";
import { useWindows } from "../../../../windowManager";
import ProcessUtils from "../../../../common/ProcessUtils";
import { unsavedProcessChanges } from "../../../../common/DialogMessages";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { displayCurrentProcessVersion, loadProcessToolbarsConfiguration } from "../../../../actions/nk";
import { useCallback } from "react";

export const useArchiveHelper = (processName: string) => {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const { confirm } = useWindows();
    const nothingToSave = useSelector(ProcessUtils.nothingToSave);
    const { redirectAfterArchive } = useSelector(getFeatureSettings);

    const archive = useCallback(async () => {
        return HttpService.archiveProcess(processName).then(() => {
            dispatch({ type: "ARCHIVED" });
            if (redirectAfterArchive) {
                navigate(ArchivedPath);
            } else {
                dispatch(loadProcessToolbarsConfiguration(processName));
                dispatch(displayCurrentProcessVersion(processName));
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
        [archive, confirm, nothingToSave],
    );

    return { confirmArchiveCallback };
};
