import type { ThunkDispatch } from "@reduxjs/toolkit";
import { createListenerMiddleware } from "@reduxjs/toolkit";
import { debounce, isEqual } from "lodash";

import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";
import React from "react";
import Notifications from "react-notification-system-redux";

import type { Action } from "../actions/reduxTypes";
import Notification from "../components/notifications/Notification";
import type { RootState } from "../reducers";
import { applyScenarioDraft, draftKey, scenarioDraftClear, scenarioDraftSet } from "../reducers/scenarioDraft";
import {
    getProcessName,
    getProcessVersionId,
    getScenarioGraph,
} from "../reducers/selectors/graph";
import { getUserSettings } from "../reducers/selectors/userSettings";

const isDraftEnabled = (state: RootState) => !!getUserSettings(state)["scenario.enableDraft"];

type AppDispatch = ThunkDispatch<RootState, undefined, Action>;

export const draftAutoSaveListener = createListenerMiddleware<RootState, AppDispatch>();

const DRAFT_OVERWRITTEN_UID = "scenario-draft-overwritten";

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

// Persist draft whenever the user edits the graph of the currently-loaded scenario.
// When undo history is empty (user is back at the clean loaded scenario), clear the
// draft so that "no undoable changes" strictly implies "no stored draft".
draftAutoSaveListener.startListening({
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

// When a fresh scenario is (re)loaded, replay the persisted draft on top of the clean
// server state as an undoable action — so the user can always undo back to the loaded scenario.
draftAutoSaveListener.startListening({
    predicate: (_action, current, previous) => {
        if (!isDraftEnabled(current)) return false;
        const prev = previous as RootState;
        const name = getProcessName(current);
        if (!name) return false;
        return getProcessName(prev) !== name || getProcessVersionId(prev) !== getProcessVersionId(current);
    },
    effect: (_action, api) => {
        const state = api.getState();
        const processName = getProcessName(state);
        if (!processName) return;
        const baseVersionId = getProcessVersionId(state);
        const draft = state.scenarioDraft[draftKey(processName, baseVersionId)];
        if (!draft) return;

        if (isEqual(draft.scenarioGraph, getScenarioGraph(state))) {
            api.dispatch(scenarioDraftClear(processName, baseVersionId));
            return;
        }

        api.dispatch(applyScenarioDraft(draft.scenarioGraph));
    },
});

// Warn the user when another tab updates the draft for the scenario/version they are looking at —
// their in-memory graph no longer matches the persisted draft.
draftAutoSaveListener.startListening({
    predicate: (action) => action.type === "SCENARIO_DRAFT_SET" && (action as { $isSync?: boolean }).$isSync === true,
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
