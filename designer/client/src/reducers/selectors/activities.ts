import { createSelector } from "reselect";

import type { ItemActivity, UIActivity } from "../../components/toolbars/activities";
import { ActivityTypesRelatedToExecutions } from "../../components/toolbars/activities/types";
import type { RootState } from "../index";

export const getActivity = (state: RootState) => state.processActivity;
export const getActivities = createSelector(getActivity, (a) => a.activities || []);

/*
 * To correctly display items in a react-window list, only the visible elements should be passed.
 **/
export const getVisibleActivities = createSelector(getActivities, (activities) =>
    activities.filter((activity) => (activity.uiType === "item" && !activity.isHidden) || activity.uiType !== "item"),
);

export function isDeploymentActivity(activity: UIActivity): activity is ItemActivity {
    if (activity.uiType !== "item") return false;
    if (activity.type === ActivityTypesRelatedToExecutions.ScenarioDeployed) return true;
    if (activity.type === ActivityTypesRelatedToExecutions.ScenarioRedeployed) return true;
    return false;
}

export const getLastDeploymentActivity = createSelector(getActivities, (activities): ItemActivity | undefined => {
    return activities.find(isDeploymentActivity);
});
