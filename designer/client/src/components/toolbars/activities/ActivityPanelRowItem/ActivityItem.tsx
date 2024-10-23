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
import { ActivityTypes } from "../types";
import { getItemColors } from "../helpers/activityItemColors";
import { useTranslation } from "react-i18next";
import { ActivityItemComment } from "./ActivityItemComment";

const StyledActivityRoot = styled("div")(({ theme }) => ({
    padding: theme.spacing(0.5, 1.25),
}));

const StyledActivityContent = styled("div")<{ isActiveFound: boolean; isFound: boolean }>(({ theme, isActiveFound, isFound }) => ({
    ...getItemColors(theme, isActiveFound, isFound),
    borderRadius: theme.spacing(0.5),
}));

const StyledActivityBody = styled("div")(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    padding: theme.spacing(1, 0.5),
    gap: theme.spacing(0.5),
}));

export const ActivityItem = forwardRef(
    (
        { activity, isRunning, searchQuery }: { activity: ItemActivity; isRunning: boolean; searchQuery: string },
        ref: ForwardedRef<HTMLDivElement>,
    ) => {
        const { t } = useTranslation();

        const actionsWithVersionInfo: ActivityTypes[] = [
            "PERFORMED_SINGLE_EXECUTION",
            "PERFORMED_SCHEDULED_EXECUTION",
            "SCENARIO_DEPLOYED",
            "SCENARIO_PAUSED",
            "SCENARIO_CANCELED",
        ];

        const version =
            activity.scenarioVersionId &&
            actionsWithVersionInfo.includes(activity.type) &&
            t("activityItem.version", "Version: {{scenarioVersionId}}", { scenarioVersionId: activity.scenarioVersionId });

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

                        {activity?.comment?.content.status === "AVAILABLE" && (
                            <ActivityItemComment comment={activity.comment} searchQuery={searchQuery} activityActions={activity.actions} />
                        )}

                        {activity?.attachment?.file.status === "DELETED" && (
                            <Typography component={SearchHighlighter} highlights={[searchQuery]} variant={"overline"}>
                                {t("activityItem.attachmentRemoved", "File ‘{{filename}}’ removed", {
                                    filename: activity.attachment.filename,
                                })}
                            </Typography>
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
