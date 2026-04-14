import { isEqual } from "lodash";
import { useCallback } from "react";
import { useStore } from "react-redux";

import { unsavedProcessChanges } from "../common/DialogMessages";
import { useUserSettings } from "../common/useUserSettings";
import { draftKey } from "../reducers/scenarioDraft";
import { getProcessName, getProcessVersionId, getScenarioGraph } from "../reducers/selectors/graph";
import { flushDraftSave, isDraftWritePending } from "../store/draftListener";
import type { AppState } from "../store/storeHelpers";
import { useWindows } from "../windowManager/useWindows";

const isDraftInSync = (state: AppState) => {
    const processName = getProcessName(state);
    if (!processName) return true;
    const versionId = getProcessVersionId(state);
    if (isDraftWritePending(processName, versionId)) return false; // backend write still in flight or failed
    const draft = state.scenarioDraft[draftKey(processName, versionId)];
    if (!draft) return true; // no draft means autosave decided current graph matches the loaded scenario
    return isEqual(draft.data, getScenarioGraph(state));
};

/**
 * Centralises the decision "do we need to warn the user about unsaved changes?".
 *
 * When the scenario.enableDraft flag is on, drafts are auto-persisted, so every navigation /
 * destructive action can proceed silently — the caller just has to flush the debounced save.
 * Otherwise the legacy DISCARD/CANCEL confirmation dialog is shown and proceed() runs only on
 * explicit user confirmation.
 */
export const useUnsavedChangesPrompt = () => {
    const [draftEnabled] = useUserSettings("scenario.enableDraft");
    const { confirm } = useWindows();
    const store = useStore<AppState>();

    const promptOrProceed = useCallback(
        (proceed: () => void, onCancel?: () => void) => {
            if (draftEnabled) {
                // Push any pending debounced edits into the draft slice + backend, then check
                // whether the in-memory graph is fully reflected in the persisted draft.
                flushDraftSave();
                if (isDraftInSync(store.getState())) {
                    proceed();
                    return;
                }
            }
            confirm({
                text: unsavedProcessChanges(),
                onConfirmCallback: (confirmed) => {
                    if (confirmed) proceed();
                    else onCancel?.();
                },
                confirmText: "DISCARD",
                denyText: "CANCEL",
            });
        },
        [confirm, draftEnabled, store],
    );

    return { draftEnabled, promptOrProceed };
};
