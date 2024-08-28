import React, { useEffect, useState } from "react";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import httpService, { ActionMetadata, ActivitiesResponse, ActivityMetadata, ActivityMetadataResponse } from "../../http/HttpService";
import { Box, styled, Typography } from "@mui/material";
import { formatDateTime } from "../../common/DateUtils";
import CommentContent from "../comment/CommentContent";
import { useSelector } from "react-redux";
import { createSelector } from "reselect";
import { getFeatureSettings } from "../../reducers/selectors/settings";

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

export const StyledActivityRoot = styled("div")(({ theme }) => ({ padding: `${theme.spacing(2)} ${theme.spacing(1)}` }));
export const StyledActivityHeader = styled("div")(({ theme }) => ({ paddingBottom: theme.spacing(0.5) }));
const getCommentSettings = createSelector(getFeatureSettings, (f) => f.commentSettings || {});

const ActivityItem = ({ activity }: { activity: Activity }) => {
    const commentSettings = useSelector(getCommentSettings);

    return (
        <StyledActivityRoot>
            <StyledActivityHeader>
                <Typography variant={"body2"}>{activity.metadata.displayableName}</Typography>
            </StyledActivityHeader>
            <Typography component={"p"} variant={"caption"}>
                {formatDateTime(activity.date)} | {activity.user}
            </Typography>
            <Typography component={"p"} variant={"caption"}>
                Version: {activity.scenarioVersionId}
            </Typography>
            {activity.comment && <CommentContent content={activity.comment} commentSettings={commentSettings} />}
        </StyledActivityRoot>
    );
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
                <ActivityItem key={activity.id} activity={activity} />
            ))}
        </ToolbarWrapper>
    );
};
