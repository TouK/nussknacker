import { RootState } from "../index";
import { createSelector } from "reselect";

export const getActivity = (state: RootState) => state.processActivity;

export const getActivities = createSelector(getActivity, (state) => state.activities || []);
