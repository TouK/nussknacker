import { createSelector } from "reselect";

import type { RootState } from "../index";

export const getHistoryPast = (state: RootState) => state.graphReducer.past;
const getHistoryFuture = (state: RootState) => state.graphReducer.future;
const getHistorySnapshots = (state: RootState) => state.graphReducer.snapshots;

const getHistorySnapshot = createSelector(getHistorySnapshots, (snapshots = []) => {
    return snapshots[snapshots.length - 1] || -1;
});

export const getHistoryCounts = createSelector(getHistoryPast, getHistoryFuture, getHistorySnapshot, (past = [], future = [], snapshot) => {
    const pastCount = past?.length || 0;
    const futureCount = future?.length || 0;
    return [pastCount, futureCount, snapshot];
});
