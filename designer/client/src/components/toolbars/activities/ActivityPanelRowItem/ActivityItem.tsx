import React, { ForwardedRef, forwardRef } from "react";
import { Box, styled, Typography } from "@mui/material";
import { formatDateTime } from "../../../../common/DateUtils";
import { ItemActivity } from "../ActivitiesPanel";
import { SearchHighlighter } from "../../creator/SearchHighlighter";
import ActivityItemHeader from "./ActivityItemHeader";
import { ActivityType } from "../types";
import { getItemColors } from "../helpers/activityItemColors";
import { useTranslation } from "react-i18next";
import { ActivityItemComment } from "./ActivityItemComment";
import { useActivityItemInfo } from "./ActivityItemProvider";

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
    padding: theme.spacing(1, 0, 1, 0.5),
    gap: theme.spacing(0.5),
}));

export const ActivityItem = forwardRef(
    (
        { activity, isDeploymentActive, searchQuery }: { activity: ItemActivity; isDeploymentActive: boolean; searchQuery: string },
        ref: ForwardedRef<HTMLDivElement>,
    ) => {
        const { t } = useTranslation();
        const { handleSetIsActivityHovered } = useActivityItemInfo();

        const actionsWithVersionInfo: ActivityType[] = [
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
            <StyledActivityRoot
                ref={ref}
                onMouseEnter={() => handleSetIsActivityHovered(true)}
                onMouseLeave={() => handleSetIsActivityHovered(false)}
            >
                <StyledActivityContent isActiveFound={activity.isActiveFound} isFound={activity.isFound}>
                    <ActivityItemHeader
                        isDeploymentActive={isDeploymentActive}
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
                            <ActivityItemComment
                                comment={activity.comment}
                                searchQuery={searchQuery}
                                activityActions={activity.actions}
                                scenarioActivityId={activity.id}
                                activityType={activity.type}
                            />
                        )}

                        {activity.additionalFields.map((additionalField, index) => {
                            const additionalFieldText = additionalField.name
                                ? `${additionalField.name}: ${additionalField.value}`
                                : additionalField.value;

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
