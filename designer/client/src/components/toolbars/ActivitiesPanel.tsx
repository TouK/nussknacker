import React, { useEffect, useState } from "react";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import httpService, { ActionMetadata, ActivitiesResponse, ActivityMetadata, ActivityMetadataResponse } from "../../http/HttpService";

type Activity = ActivitiesResponse["activities"][number] & { metadata: ActivityMetadata; actions: ActionMetadata[] };

const mergeActivityDataWithMetadata = (
    activities: ActivitiesResponse["activities"],
    activitiesMetadata: ActivityMetadataResponse,
): Activity[] => {
    return activities.map((activity): Activity => {
        const metadata = activitiesMetadata.activities.find((activityMetadata) => activityMetadata.type === activity.type);
        const actions = metadata.supportedActions.map((supportedAction) => {
            return activitiesMetadata.actions.find((action) => action.id === supportedAction);
        });

        return { ...activity, metadata, actions };
    });
};
export const ActivitiesPanel = (props: ToolbarPanelProps) => {
    const [data, setData] = useState<Activity[]>([]);

    useEffect(() => {
        Promise.all([httpService.fetchActivitiesMetadata(), httpService.fetchActivities()]).then(([activitiesMetadata, { activities }]) => {
            setData(mergeActivityDataWithMetadata(activities, activitiesMetadata));
        });
    }, []);

    if (!data.length) return;

    return (
        <ToolbarWrapper {...props} title={"Activities"}>
            {data.map((activity) => (
                <div key={activity.id}>
                    {activity.metadata.displayableName} {activity.actions.map((value) => value?.displayableName)}
                </div>
            ))}
        </ToolbarWrapper>
    );
};
