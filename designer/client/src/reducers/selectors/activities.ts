import { createSelector } from "reselect";

import type { UIActivity } from "../../components/toolbars/activities";
import { getActivityId } from "../../components/toolbars/activities";
import type { RootState } from "../index";

export function applySearchResults(activity: UIActivity, foundActivities: string[], selectedResult: number) {
    if (activity.uiType !== "item") {
        return activity;
    }

    return {
        ...activity,
        isFound: foundActivities.some((foundResult) => foundResult === getActivityId(activity)),
        isActiveFound: getActivityId(activity) === foundActivities[selectedResult],
    };
}

const getActivity = (state: RootState) => state.processActivity;

/*
 * To correctly display items in a react-window list, only the visible elements should be passed.
 **/
export const getVisibleActivities = createSelector(getActivity, ({ activities = [], foundActivities = [], selectedResult = 0 }) => {
    return activities
        .filter((activity) => (activity.uiType === "item" && !activity.isHidden) || activity.uiType !== "item")
        .map((activity) => applySearchResults(activity, foundActivities, selectedResult));
});
