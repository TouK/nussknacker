import { RootState } from "../index";
import { createSelector } from "reselect";

export const getActivity = (state: RootState) => state.processActivity;

/*
 * To correctly display items in a react-window list, only the visible elements should be passed.
 **/
export const getVisibleActivities = createSelector(
    getActivity,
    (state) =>
        state.activities.filter((activity) => (activity.uiType === "item" && !activity.isHidden) || activity.uiType !== "item") || [],
);
