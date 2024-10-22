import React, { ForwardedRef, forwardRef } from "react";
import { useSelector } from "react-redux";
import { Box, styled, Typography } from "@mui/material";
import { formatDateTime } from "../../../../common/DateUtils";
import CommentContent from "../../../comment/CommentContent";
import { createSelector } from "reselect";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { ItemActivity } from "../ActivitiesPanel";
import { SearchHighlighter } from "../../creator/SearchHighlighter";
import ActivityItemHeader from "./ActivityItemHeader";
import { ActivityTypes } from "../../../../http/HttpService";
import { getItemColors } from "../helpers/activityItemColors";

const StyledActivityRoot = styled("div")(({ theme }) => ({
    padding: theme.spacing(0.5, 1.25),
}));

const StyledActivityContent = styled("div")<{ isActiveFound: boolean; isFound: boolean }>(({ theme, isActiveFound, isFound }) => ({
    ...getItemColors(theme, isActiveFound, isFound),
    borderRadius: theme.spacing(1),
}));

const StyledActivityBody = styled("div")(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    padding: theme.spacing(0.5, 0.5),
    gap: theme.spacing(0.5),
}));

const getCommentSettings = createSelector(getFeatureSettings, (f) => f.commentSettings || {});

export const ActivityItem = forwardRef(
    (
        { activity, isRunning, searchQuery }: { activity: ItemActivity; isRunning: boolean; searchQuery: string },
        ref: ForwardedRef<HTMLDivElement>,
    ) => {
        const commentSettings = useSelector(getCommentSettings);

        const actionsWithVersionInfo: ActivityTypes[] = [
            "PERFORMED_SINGLE_EXECUTION",
            "PERFORMED_SCHEDULED_EXECUTION",
            "SCENARIO_DEPLOYED",
            "SCENARIO_PAUSED",
            "SCENARIO_CANCELED",
        ];

        const version =
            activity.scenarioVersionId && actionsWithVersionInfo.includes(activity.type) && `Version: ${activity.scenarioVersionId}`;

        return (
            <StyledActivityRoot ref={ref}>
                <StyledActivityContent isActiveFound={activity.isActiveFound} isFound={activity.isFound}>
                    <ActivityItemHeader
                        isRunning={isRunning}
                        searchQuery={searchQuery}
                        activity={activity}
                        isActiveFound={activity.isActiveFound}
                        isFound={activity.isFound}
                    />
                    <StyledActivityBody>
                        <Box display={"flex"} flexWrap={"wrap"}>
                            <Box display={"flex"} alignItems={"center"} justifyContent={"flex-start"} flexBasis={"100%"}>
                                <Typography
                                    component={SearchHighlighter}
                                    highlights={[searchQuery]}
                                    variant={"overline"}
                                    data-testid={"activity-date"}
                                >
                                    {formatDateTime(activity.date)}
                                </Typography>
                                <Box component={Typography} variant={"overline"} px={0.5}>
                                    |
                                </Box>
                                <Typography component={SearchHighlighter} highlights={[searchQuery]} variant={"overline"}>
                                    {activity.user}
                                </Typography>
                            </Box>

                            {version && <Typography variant={"overline"}>{version}</Typography>}
                        </Box>

                        {activity?.comment?.content?.value && (
                            <CommentContent
                                content={activity.comment.content.value}
                                commentSettings={commentSettings}
                                searchWords={[searchQuery]}
                                variant={"overline"}
                            />
                        )}
                        {activity.additionalFields.map((additionalField, index) => {
                            const additionalFieldText = `${additionalField.name}: ${additionalField.value}`;

                            return (
                                <Typography component={SearchHighlighter} highlights={[searchQuery]} key={index} variant={"overline"}>
                                    {additionalFieldText}
                                </Typography>
                            );
                        })}
                    </StyledActivityBody>
                </StyledActivityContent>
            </StyledActivityRoot>
        );
    },
);

ActivityItem.displayName = "ActivityItem";
