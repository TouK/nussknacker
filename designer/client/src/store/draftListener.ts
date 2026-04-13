import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";
import type { ThunkDispatch } from "@reduxjs/toolkit";
import { createListenerMiddleware, createSelector } from "@reduxjs/toolkit";
import { debounce, isEqual } from "lodash";
import React from "react";
import Notifications from "react-notification-system-redux";

import type { Action } from "../actions/reduxTypes";
import Notification from "../components/notifications/Notification";
import type { ProcessName } from "../components/Process/types";
import { getScenarioDraftBackend } from "../draftStorage";
import type { RootState } from "../reducers";
import type { ScenarioDraft, ScenarioDraftState } from "../reducers/scenarioDraft";
import {
    applyScenarioDraft,
    draftKey,
    scenarioDraftClear,
    scenarioDraftHydrate,
    scenarioDraftSet,
} from "../reducers/scenarioDraft";
import { getProcessName, getProcessVersionId, getScenarioGraph } from "../reducers/selectors/graph";
import { getUserSettings } from "../reducers/selectors/userSettings";

type AppDispatch = ThunkDispatch<RootState, undefined, Action>;

const isDraftEnabled = createSelector(getUserSettings, (settings) => !!settings["scenario.enableDraft"]);
const wasSyncedFromOtherTab = (action: unknown) => (action as { $isSync?: boolean }).$isSync === true;

const DRAFT_OVERWRITTEN_UID = "scenario-draft-overwritten";

export const draftListener = createListenerMiddleware<RootState, AppDispatch>();

const debouncedPersist = debounce((dispatch: AppDispatch, state: RootState) => {
    const processName = getProcessName(state);
    if (!processName) return;
    dispatch(
        scenarioDraftSet({
            processName,
            baseVersionId: getProcessVersionId(state),
            scenarioGraph: getScenarioGraph(state),
            updatedAt: new Date().toISOString(),
        }),
    );
}, 500);

export const flushDraftSave = () => debouncedPersist.flush();

// ---- autosave: local edits -> redux slice ---------------------------------------------------
// When undo history is empty (user returned to the clean loaded scenario), clear the draft so
// that "no undoable changes" strictly implies "no stored draft".
draftListener.startListening({
    predicate: (_action, current, previous) => {
        const prev = previous as RootState;
        return (
            isDraftEnabled(current) &&
            getProcessName(current) !== null &&
            getProcessName(current) === getProcessName(prev) &&
            getProcessVersionId(current) === getProcessVersionId(prev) &&
            getScenarioGraph(current) !== getScenarioGraph(prev)
        );
    },
    effect: (_action, api) => {
        const state = api.getState();
        const processName = getProcessName(state);
        const baseVersionId = getProcessVersionId(state);
        if (!processName) return;
        api.dispatch(Notifications.hide(DRAFT_OVERWRITTEN_UID));
        if (state.graphReducer.past.length === 0) {
            debouncedPersist.cancel();
            if (state.scenarioDraft[draftKey(processName, baseVersionId)]) {
                api.dispatch(scenarioDraftClear(processName, baseVersionId));
            }
            return;
        }
        debouncedPersist(api.dispatch, state);
    },
});

// ---- persistence: slice changes -> backend --------------------------------------------------
// Skip writes that arrived via redux-state-sync; the originating tab already wrote its entry.
draftListener.startListening({
    predicate: (action) => action.type === "SCENARIO_DRAFT_SET" && !wasSyncedFromOtherTab(action),
    effect: (action) => {
        const payload = (action as unknown as { payload: ScenarioDraft }).payload;
        getScenarioDraftBackend()
            .set(payload.processName, payload.baseVersionId, JSON.stringify(payload))
            .catch(() => {
                // best-effort
            });
    },
});

draftListener.startListening({
    predicate: (action) => action.type === "SCENARIO_DRAFT_CLEAR" && !wasSyncedFromOtherTab(action),
    effect: (action) => {
        const { processName, baseVersionId } = action as unknown as {
            processName: ProcessName;
            baseVersionId: number | null;
        };
        getScenarioDraftBackend()
            .remove(processName, baseVersionId)
            .catch(() => {
                // best-effort
            });
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
        const prev = previous as RootState;
        return getProcessName(prev) !== name || getProcessVersionId(prev) !== getProcessVersionId(current);
    },
    effect: async (_action, api) => {
        const processName = getProcessName(api.getState());
        if (!processName) return;

        const nameChanged = getProcessName(api.getOriginalState()) !== processName;
        if (nameChanged) {
            try {
                const entries = await getScenarioDraftBackend().list(processName);
                const drafts: ScenarioDraftState = {};
                for (const { versionId, value } of entries) {
                    try {
                        drafts[draftKey(processName, versionId)] = JSON.parse(value) as ScenarioDraft;
                    } catch {
                        // skip corrupted entry
                    }
                }
                api.dispatch(scenarioDraftHydrate(drafts));
            } catch {
                // backend unavailable — leave state untouched
            }
        }

        const state = api.getState();
        const versionId = getProcessVersionId(state);
        const draft = state.scenarioDraft[draftKey(processName, versionId)];
        if (!draft) return;

        if (isEqual(draft.scenarioGraph, getScenarioGraph(state))) {
            api.dispatch(scenarioDraftClear(processName, versionId));
            return;
        }
        api.dispatch(applyScenarioDraft(draft.scenarioGraph));
    },
});

// ---- cross-tab overwrite warning ------------------------------------------------------------
// Another tab updated the draft for the scenario/version we are looking at — the in-memory
// graph no longer matches the persisted draft. Sticky notification; dismissed by the user or
// by the autosave listener above on the next local edit.
draftListener.startListening({
    predicate: (action) => action.type === "SCENARIO_DRAFT_SET" && wasSyncedFromOtherTab(action),
    effect: (action, api) => {
        const payload = (action as unknown as { payload: { processName: string; baseVersionId: number | null } }).payload;
        const state = api.getState();
        if (payload.processName !== getProcessName(state) || payload.baseVersionId !== getProcessVersionId(state)) return;
        api.dispatch(
            Notifications.warning({
                uid: DRAFT_OVERWRITTEN_UID,
                autoDismiss: 0,
                dismissible: "button",
                children: React.createElement(Notification, {
                    type: "warning",
                    icon: React.createElement(WarningAmberOutlinedIcon),
                    message: "Draft for this version was updated in another session — refresh to see the latest changes.",
                }),
            }),
        );
    },
});
