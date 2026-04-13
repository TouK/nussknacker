import type { ThunkDispatch } from "@reduxjs/toolkit";
import { createListenerMiddleware, createSelector } from "@reduxjs/toolkit";
import { debounce, isEqual } from "lodash";
import Notifications from "react-notification-system-redux";

import { warn } from "../actions/notificationActions";
import type { Action } from "../actions/reduxTypes";
import type { ScenarioDraftActions, ScenarioDraftState } from "../actions/scenarioDraftActions";
import { applyScenarioDraft, scenarioDraftClear, scenarioDraftHydrate, scenarioDraftSet } from "../actions/scenarioDraftActions";
import type { ProcessName } from "../components/Process/types";
import { getScenarioDraftBackend } from "../draftStorage";
import type { RootState } from "../reducers";
import { draftKey } from "../reducers/scenarioDraft";
import { getProcessName, getProcessVersionId, getScenarioGraph } from "../reducers/selectors/graph";
import { getUserSettings } from "../reducers/selectors/userSettings";

type AppDispatch = ThunkDispatch<RootState, undefined, Action>;

const isDraftEnabled = createSelector(getUserSettings, (settings) => !!settings["scenario.enableDraft"]);

type SyncMarker = { $isSync?: boolean };
type NarrowedAction<T extends ScenarioDraftActions["type"]> = Extract<ScenarioDraftActions, { type: T }> & SyncMarker;

const wasSyncedFromOtherTab = (action: SyncMarker) => action.$isSync === true;

const DRAFT_OVERWRITTEN_UID = "scenario-draft-overwritten";
const VERSION_BUMPED_UID = "scenario-version-bumped";

export const draftListener = createListenerMiddleware<RootState, AppDispatch>();

const debouncedPersist = debounce((dispatch: AppDispatch) => {
    dispatch(scenarioDraftSet());
}, 500);

export const flushDraftSave = () => debouncedPersist.flush();

// Tracks draft mutations whose backend write is in-flight or has failed. While a key is in
// this set the in-memory draft has not been safely persisted, so the unsaved-changes prompt /
// beforeunload alert must remain active.
const pendingBackendWrites = new Set<string>();

export const hasUnpersistedDraftWrites = () => pendingBackendWrites.size > 0;
export const isDraftWritePending = (processName: ProcessName, versionId: number | null) =>
    pendingBackendWrites.has(draftKey(processName, versionId));

// ---- autosave: local edits -> redux slice ---------------------------------------------------
// When undo history is empty (user returned to the clean loaded scenario), clear the draft so
// that "no undoable changes" strictly implies "no stored draft".
draftListener.startListening({
    predicate: (_action, current, previous) => {
        return (
            isDraftEnabled(current) &&
            getProcessName(current) !== null &&
            getProcessName(current) === getProcessName(previous) &&
            getProcessVersionId(current) === getProcessVersionId(previous) &&
            getScenarioGraph(current) !== getScenarioGraph(previous)
        );
    },
    effect: (_action, { dispatch, getState }) => {
        const state = getState();
        const processName = getProcessName(state);
        const baseVersionId = getProcessVersionId(state);
        if (!processName) return;
        dispatch(Notifications.hide(DRAFT_OVERWRITTEN_UID));
        if (state.graphReducer.past.length === 0) {
            debouncedPersist.cancel();
            if (state.scenarioDraft[draftKey(processName, baseVersionId)]) {
                dispatch(scenarioDraftClear(processName, baseVersionId));
            }
            return;
        }
        debouncedPersist(dispatch);
    },
});

// ---- SCENARIO_DRAFT_SET: local writes -> backend; remote writes -> overwrite warning --------
draftListener.startListening({
    matcher: (action): action is NarrowedAction<"SCENARIO_DRAFT_SET"> => action.type === "SCENARIO_DRAFT_SET",
    effect: (action, { dispatch, getState }) => {
        const { payload } = action;
        if (wasSyncedFromOtherTab(action)) {
            // Another tab updated the draft for the scenario/version we are looking at — the
            // in-memory graph no longer matches the persisted draft. Sticky notification;
            // dismissed by the user or by the autosave listener on the next local edit.
            const state = getState();
            if (payload.id !== getProcessName(state)) return;
            if (payload.baseVersionId !== getProcessVersionId(state)) return;
            dispatch(
                warn("Draft for this version was updated in another session — refresh to see the latest changes.", {
                    uid: DRAFT_OVERWRITTEN_UID,
                    autoDismiss: 0,
                }),
            );
            return;
        }
        const key = draftKey(payload.id, payload.baseVersionId);
        pendingBackendWrites.add(key);
        getScenarioDraftBackend()
            .set(payload.id, payload.baseVersionId, payload)
            .then(() => pendingBackendWrites.delete(key))
            .catch(() => {
                // keep key in the pending set — the prompt / beforeunload alert will warn the user
            });
    },
});

// ---- SCENARIO_DRAFT_CLEAR: local clears -> backend; remote clears -> overwrite warning ------
draftListener.startListening({
    matcher: (action): action is NarrowedAction<"SCENARIO_DRAFT_CLEAR"> => action.type === "SCENARIO_DRAFT_CLEAR",
    effect: (action, { dispatch, getState }) => {
        const { processName, baseVersionId } = action;
        if (wasSyncedFromOtherTab(action)) {
            const state = getState();
            if (processName !== getProcessName(state)) return;
            if (baseVersionId !== getProcessVersionId(state)) return;
            dispatch(
                warn("Draft for this version was discarded in another session — refresh to see the latest changes.", {
                    uid: DRAFT_OVERWRITTEN_UID,
                    autoDismiss: 0,
                }),
            );
            return;
        }
        const key = draftKey(processName, baseVersionId);
        pendingBackendWrites.add(key);
        getScenarioDraftBackend()
            .remove(processName, baseVersionId)
            .then(() => pendingBackendWrites.delete(key))
            .catch(() => {
                // keep key in the pending set
            });
    },
});

// ---- SCENARIO_VERSION_BUMPED: other session saved a new version of the scenario we are viewing
draftListener.startListening({
    matcher: (action): action is NarrowedAction<"SCENARIO_VERSION_BUMPED"> =>
        action.type === "SCENARIO_VERSION_BUMPED" && wasSyncedFromOtherTab(action),
    effect: ({ processName, versionId }, { dispatch, getState }) => {
        const state = getState();
        if (processName !== getProcessName(state)) return;
        if (versionId === getProcessVersionId(state)) return;
        // A save produces both a DRAFT_CLEAR and a VERSION_BUMPED broadcast; hide the stale
        // draft-overwritten notification so only the version-bump one remains.
        dispatch(Notifications.hide(DRAFT_OVERWRITTEN_UID));
        dispatch(
            warn(`Scenario was saved in another session as version ${versionId} — refresh to see the new version.`, {
                uid: VERSION_BUMPED_UID,
                autoDismiss: 0,
            }),
        );
    },
});

// ---- hydrate + restore on scenario/version change -------------------------------------------
// On scenario name change we pull all drafts for that scenario from the backend. On either name
// or version change we replay any matching draft on top of the clean server state as an
// undoable action, so the user can undo back to the loaded scenario.
draftListener.startListening({
    predicate: (_action, current, previous) => {
        if (!isDraftEnabled(current)) return false;
        const name = getProcessName(current);
        if (!name) return false;
        return getProcessName(previous) !== name || getProcessVersionId(previous) !== getProcessVersionId(current);
    },
    effect: async (_action, { dispatch, getOriginalState, getState }) => {
        const processName = getProcessName(getState());
        if (!processName) return;

        const nameChanged = getProcessName(getOriginalState()) !== processName;
        if (nameChanged) {
            try {
                const entries = await getScenarioDraftBackend().list(processName);
                const drafts: ScenarioDraftState = {};
                for (const { versionId, value } of entries) {
                    drafts[draftKey(processName, versionId)] = value;
                }
                dispatch(scenarioDraftHydrate(drafts));
            } catch {
                // backend unavailable — leave state untouched
            }
        }

        const state = getState();
        const versionId = getProcessVersionId(state);
        const draft = state.scenarioDraft[draftKey(processName, versionId)];
        if (!draft) return;

        if (isEqual(draft.data, getScenarioGraph(state))) {
            dispatch(scenarioDraftClear(processName, versionId));
            return;
        }
        dispatch(applyScenarioDraft(draft.data));
    },
});
