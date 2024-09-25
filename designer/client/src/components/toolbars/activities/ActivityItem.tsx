import React, { ForwardedRef, forwardRef } from "react";
import { useSelector } from "react-redux";
import { Box, styled, Typography } from "@mui/material";
import { formatDateTime } from "../../../common/DateUtils";
import CommentContent from "../../comment/CommentContent";
import { createSelector } from "reselect";
import { getFeatureSettings } from "../../../reducers/selectors/settings";
import { blend } from "@mui/system";
import { blendLighten } from "../../../containers/theme/helpers";
import { ItemActivity } from "./ActivitiesPanel";
import { SearchHighlighter } from "../creator/SearchHighlighter";
import ActivityItemHeader from "./ActivityItemHeader";

const StyledActivityRoot = styled("div")(({ theme }) => ({
    padding: `${theme.spacing(1)} ${theme.spacing(1)} ${theme.spacing(2)}`,
}));

const StyledActivityContent = styled("div")<{ isActiveFound: boolean; isFound: boolean }>(({ theme, isActiveFound, isFound }) => ({
    border: isActiveFound
        ? `0.5px solid ${blendLighten(theme.palette.primary.main, 0.7)}`
        : isFound
        ? `0.5px solid ${blendLighten(theme.palette.primary.main, 0.6)}`
        : "none",
    borderRadius: "4px",
    backgroundColor: isActiveFound
        ? blend(theme.palette.background.paper, theme.palette.primary.main, 0.16)
        : isFound
        ? blend(theme.palette.background.paper, theme.palette.primary.main, 0.08)
        : "none",
}));

const StyledActivityBody = styled("div")(({ theme }) => ({
    margin: theme.spacing(1),
}));

const getCommentSettings = createSelector(getFeatureSettings, (f) => f.commentSettings || {});

export const ActivityItem = forwardRef(
    (
        { activity, isActiveItem, searchQuery }: { activity: ItemActivity; isActiveItem: boolean; searchQuery: string },
        ref: ForwardedRef<HTMLDivElement>,
    ) => {
        const commentSettings = useSelector(getCommentSettings);

        const version = `Version: ${activity.scenarioVersionId}`;

        return (
            <StyledActivityRoot ref={ref}>
                <StyledActivityContent isActiveFound={activity.isActiveFound} isFound={activity.isFound}>
                    <ActivityItemHeader
                        isActiveItem={isActiveItem}
                        searchQuery={searchQuery}
                        activity={activity}
                        scenarioVersion={activity.scenarioVersionId}
                    />
                    <StyledActivityBody>
                        <Box display={"flex"} alignItems={"center"} justifyContent={"flex-start"}>
                            <Typography component={SearchHighlighter} highlights={[searchQuery]} variant={"overline"}>
                                {formatDateTime(activity.date)}
                            </Typography>
                            <Box component={Typography} variant={"overline"} px={0.5}>
                                |
                            </Box>
                            <Typography component={SearchHighlighter} highlights={[searchQuery]} variant={"overline"}>
                                {activity.user}
                            </Typography>
                        </Box>

                        {activity.scenarioVersionId && (
                            <Typography component={SearchHighlighter} highlights={[searchQuery]} variant={"overline"}>
                                {version}
                            </Typography>
                        )}
                        {activity.comment && (
                            <CommentContent
                                content={activity.comment.content.value}
                                commentSettings={commentSettings}
                                searchWords={[searchQuery]}
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
