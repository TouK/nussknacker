import { RootState } from "../index";
import { createSelectorCreator, defaultMemoize } from "reselect";
import { isEqual } from "lodash";

const createDeepEqualSelector = createSelectorCreator(defaultMemoize, isEqual);

export const getActivity = (state: RootState) => state.processActivity;

/*
 * To correctly display items in a react-window list, only the visible elements should be passed.
 **/
export const getVisibleActivities = createDeepEqualSelector(
    getActivity,
    (state) =>
        state.activities.filter((activity) => (activity.uiType === "item" && !activity.isHidden) || activity.uiType !== "item") || [],
);
