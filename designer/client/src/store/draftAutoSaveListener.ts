import type { ThunkDispatch } from "@reduxjs/toolkit";
import { createListenerMiddleware } from "@reduxjs/toolkit";
import { debounce, isEqual } from "lodash";

import type { Action } from "../actions/reduxTypes";
import type { RootState } from "../reducers";
import { applyScenarioDraft, scenarioDraftClear, scenarioDraftSet } from "../reducers/scenarioDraft";
import {
    getProcessName,
    getProcessVersionId,
    getScenarioGraph,
} from "../reducers/selectors/graph";
import { getUserSettings } from "../reducers/selectors/userSettings";

const isDraftEnabled = (state: RootState) => !!getUserSettings(state)["scenario.enableDraft"];

type AppDispatch = ThunkDispatch<RootState, undefined, Action>;

export const draftAutoSaveListener = createListenerMiddleware<RootState, AppDispatch>();

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
        if (state.graphReducer.past.length === 0) {
            debouncedPersist.cancel();
            if (state.scenarioDraft) api.dispatch(scenarioDraftClear());
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
        const draft = state.scenarioDraft;
        if (!processName || !draft || draft.processName !== processName) return;

        if (draft.baseVersionId !== getProcessVersionId(state)) {
            api.dispatch(scenarioDraftClear());
            return;
        }
        if (isEqual(draft.scenarioGraph, getScenarioGraph(state))) {
            api.dispatch(scenarioDraftClear());
            return;
        }

        api.dispatch(applyScenarioDraft(draft.scenarioGraph));
    },
});
