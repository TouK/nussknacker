import React, { ForwardedRef, forwardRef } from "react";
import { useSelector } from "react-redux";
import { Box, styled, Typography } from "@mui/material";
import { formatDateTime } from "../../../common/DateUtils";
import CommentContent from "../../comment/CommentContent";
import { createSelector } from "reselect";
import { getFeatureSettings } from "../../../reducers/selectors/settings";
import { ActionMetadata } from "../../../http/HttpService";
import { blend } from "@mui/system";
import { blendLighten, getBorderColor } from "../../../containers/theme/helpers";
import UrlIcon from "../../UrlIcon";
import { ItemActivity } from "./ActivitiesPanel";
import { SearchHighlighter } from "../creator/SearchHighlighter";

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

const StyledActivityHeader = styled("div")<{ isHighlighted: boolean; isActive: boolean }>(({ theme, isHighlighted, isActive }) => ({
    display: "flex",
    alignItems: "center",
    padding: theme.spacing(0.5),
    backgroundColor: isActive
        ? blend(theme.palette.background.paper, theme.palette.primary.main, 0.2)
        : isHighlighted
        ? blend(theme.palette.background.paper, theme.palette.primary.main, 0.05)
        : undefined,
    border: (isActive || isHighlighted) && `1px solid ${getBorderColor(theme)}`,
    borderRadius: theme.spacing(1),
}));
const StyledActivityBody = styled("div")(({ theme }) => ({
    margin: theme.spacing(1),
}));
const StyledHeaderIcon = styled(UrlIcon)(({ theme }) => ({
    width: "16px",
    height: "16px",
    marginRight: theme.spacing(1),
}));

const StyledHeaderActionIcon = styled(UrlIcon)(() => ({
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

export const ActivityItem = forwardRef(
    (
        { activity, isActiveItem, searchQuery }: { activity: ItemActivity; isActiveItem: boolean; searchQuery: string },
        ref: ForwardedRef<HTMLDivElement>,
    ) => {
        const commentSettings = useSelector(getCommentSettings);

        const isHighlighted = ["SCENARIO_DEPLOYED", "SCENARIO_CANCELED"].includes(activity.type);
        const version = `Version: ${activity.scenarioVersionId}`;

        return (
            <StyledActivityRoot ref={ref}>
                <StyledActivityContent isActiveFound={activity.isActiveFound} isFound={activity.isFound}>
                    <StyledActivityHeader isHighlighted={isHighlighted} isActive={isActiveItem}>
                        <StyledHeaderIcon src={activity.activities.icon} />
                        <Typography
                            variant={"caption"}
                            component={SearchHighlighter}
                            highlights={[searchQuery]}
                            sx={(theme) => ({ color: theme.palette.text.primary })}
                        >
                            {activity.activities.displayableName}
                        </Typography>

                        {activity.actions.map((activityAction) => (
                            <HeaderActivity key={activityAction.id} activityAction={activityAction} />
                        ))}
                    </StyledActivityHeader>
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
                            <CommentContent content={activity.comment} commentSettings={commentSettings} searchWords={[searchQuery]} />
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
