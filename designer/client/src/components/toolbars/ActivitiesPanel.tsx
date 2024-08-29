import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import httpService, { ActionMetadata, ActivitiesResponse, ActivityMetadata, ActivityMetadataResponse } from "../../http/HttpService";
import { styled, Typography } from "@mui/material";
import { formatDateTime } from "../../common/DateUtils";
import CommentContent from "../comment/CommentContent";
import { useSelector } from "react-redux";
import { createSelector } from "reselect";
import { getFeatureSettings } from "../../reducers/selectors/settings";
import UrlIcon from "../UrlIcon";
import { getBorderColor } from "../../containers/theme/helpers";
import { blend } from "@mui/system";
import { VariableSizeList } from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";

type Activity = ActivitiesResponse["activities"][number] & { activities: ActivityMetadata; actions: ActionMetadata[] };

const mergeActivityDataWithMetadata = (
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

export const StyledActivityRoot = styled("div")(({ theme }) => ({
    padding: `${theme.spacing(1)} ${theme.spacing(1)} ${theme.spacing(4)}`,
}));
export const StyledActivityHeader = styled("div")(({ theme }) => ({
    display: "flex",
    alignItems: "center",
    padding: theme.spacing(1),
    backgroundColor: blend(theme.palette.background.paper, theme.palette.primary.main, 0.2),
    border: `1px solid ${getBorderColor(theme)}`,
    borderRadius: theme.spacing(1),
}));
export const StyledActivityBody = styled("div")(({ theme }) => ({
    margin: theme.spacing(1),
}));
export const StyledHeaderIcon = styled(UrlIcon)(({ theme }) => ({
    width: "16px",
    height: "16px",
    marginRight: theme.spacing(1),
}));

export const StyledHeaderActionIcon = styled(UrlIcon)(({ theme }) => ({
    width: "16px",
    height: "16px",
    marginLeft: "auto",
    cursor: "pointer",
}));

const getCommentSettings = createSelector(getFeatureSettings, (f) => f.commentSettings || {});

const HeaderActivity = ({ activityAction }: { activityAction: ActionMetadata }) => {
    switch (activityAction.id) {
        case "compare": {
            return (
                <StyledHeaderActionIcon
                    onClick={() => {
                        alert(`action called: ${activityAction.id}`);
                    }}
                    key={activityAction.id}
                    src={activityAction.icon}
                />
            );
        }
        default: {
            return null;
        }
    }
};

const ActivityItem = ({ activity }: { activity: Activity }) => {
    const commentSettings = useSelector(getCommentSettings);

    return (
        <StyledActivityRoot>
            <StyledActivityHeader>
                <StyledHeaderIcon src={activity.activities.icon} />
                <Typography variant={"body2"}>{activity.activities.displayableName}</Typography>
                {activity.actions.map((activityAction) => (
                    <HeaderActivity key={activityAction.id} activityAction={activityAction} />
                ))}
            </StyledActivityHeader>
            <StyledActivityBody>
                <Typography mt={0.5} component={"p"} variant={"caption"}>
                    {formatDateTime(activity.date)} | {activity.user}
                </Typography>
                {activity.scenarioVersionId && (
                    <Typography component={"p"} variant={"caption"}>
                        Version: {activity.scenarioVersionId}
                    </Typography>
                )}
                {activity.comment && <CommentContent content={activity.comment} commentSettings={commentSettings} />}
                {activity.additionalFields.map((additionalField, index) => (
                    <Typography key={index} component={"p"} variant={"caption"}>
                        {additionalField.name}: {additionalField.value}
                    </Typography>
                ))}
            </StyledActivityBody>
        </StyledActivityRoot>
    );
};

export const ActivitiesPanel = (props: ToolbarPanelProps) => {
    const listRef = useRef<VariableSizeList>(null);
    const rowHeights = useRef({});

    const setRowHeight = useCallback((index, size) => {
        if (listRef.current) {
            listRef.current.resetAfterIndex(index);
        }

        rowHeights.current = { ...rowHeights.current, [index]: size };
    }, []);

    const getRowHeight = useCallback((index: number) => {
        return rowHeights.current[index];
    }, []);

    const [data, setData] = useState<Activity[]>([]);

    useEffect(() => {
        Promise.all([httpService.fetchActivitiesMetadata(), httpService.fetchActivities()]).then(([activitiesMetadata, { activities }]) => {
            setData(mergeActivityDataWithMetadata(activities, activitiesMetadata));
        });
    }, []);

    if (!data.length) return;

    const Row = ({ index, style }) => {
        const rowRef = useRef<HTMLDivElement>(null);
        const activity = useMemo(() => data[index], [index]);
        useEffect(() => {
            if (rowRef.current) {
                setRowHeight(index, rowRef.current.clientHeight);
            }
        }, [index, rowRef]);

        return (
            <div key={activity.id} ref={rowRef} style={style}>
                <ActivityItem activity={activity} />
            </div>
        );
    };

    return (
        <ToolbarWrapper {...props} title={"Activities"}>
            <div style={{ width: "100%", height: "500px" }}>
                <AutoSizer>
                    {({ width, height }) => (
                        <VariableSizeList
                            ref={listRef}
                            itemCount={data.length}
                            estimatedItemSize={100}
                            itemSize={getRowHeight}
                            height={height}
                            width={width}
                        >
                            {Row}
                        </VariableSizeList>
                    )}
                </AutoSizer>
            </div>
        </ToolbarWrapper>
    );
};
