import { createSelector } from "reselect";

import type { RootState } from "../index";

const getProcessActivityState = (state: RootState) => {
    return state.processActivity;
};

export const getSearchQuery = createSelector(getProcessActivityState, (processActivity) => {
    return processActivity.searchQuery;
});
