import { useNavigate } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { ArchivedPath } from "../../../../containers/paths";
import HttpService from "../../../../http/HttpService";
import { useWindows } from "../../../../windowManager";
import ProcessUtils from "../../../../common/ProcessUtils";
import { unsavedProcessChanges } from "../../../../common/DialogMessages";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { displayCurrentProcessVersion, loadProcessToolbarsConfiguration } from "../../../../actions/nk";

export const useArchiveHelper = (processId: string) => {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const { confirm } = useWindows();
    const nothingToSave = useSelector(ProcessUtils.nothingToSave);
    const { redirectAfterArchive } = useSelector(getFeatureSettings);

    const changeRestoreHistoryStatus = () => {
        dispatch({
            type: "RESTORE_HISTORY",
            restoreHistory: true,
        });
    };

    const archive = async () => {
        return HttpService.archiveProcess(processId).then(() => {
            if (redirectAfterArchive) {
                navigate(ArchivedPath);
            } else {
                dispatch(loadProcessToolbarsConfiguration(processId));
                dispatch(displayCurrentProcessVersion(processId));
            }
        });
    };

    const confirmArchiveCallback = async (confirmed: boolean) => {
        if (confirmed && !nothingToSave) {
            await confirm({
                text: unsavedProcessChanges(),
                onConfirmCallback: async (confirmed) => {
                    if (confirmed) {
                        changeRestoreHistoryStatus();
                        return await archive();
                    }
                },
                confirmText: "DISCARD",
                denyText: "CANCEL",
            });
        } else if (confirmed) {
            await archive();
        }
    };

    return { confirmArchiveCallback };
};
