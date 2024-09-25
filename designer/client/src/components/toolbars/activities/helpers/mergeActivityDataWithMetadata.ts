import { ActivitiesResponse, ActivityMetadataResponse } from "../../../../http/HttpService";
import { Activity } from "../ActivitiesPanel";

export const mergeActivityDataWithMetadata = (
    activities: ActivitiesResponse["activities"],
    activitiesMetadata: ActivityMetadataResponse,
): Activity[] => {
    return activities.map((activity): Activity => {
        const activities = activitiesMetadata.activities.find((activityMetadata) => activityMetadata.type === activity.type);
        const actions = activities.supportedActions.map((supportedAction) => {
            return activitiesMetadata.actions.find((action) => action.id === supportedAction);
        });

        return { ...activity, activities, actions };
    });
};
