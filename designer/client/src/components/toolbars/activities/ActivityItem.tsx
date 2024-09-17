import React, { ForwardedRef, forwardRef } from "react";
import { useSelector } from "react-redux";
import { styled, Typography } from "@mui/material";
import { formatDateTime } from "../../../common/DateUtils";
import CommentContent from "../../comment/CommentContent";
import { createSelector } from "reselect";
import { getFeatureSettings } from "../../../reducers/selectors/settings";
import { ActionMetadata } from "../../../http/HttpService";
import { blend } from "@mui/system";
import { getBorderColor } from "../../../containers/theme/helpers";
import UrlIcon from "../../UrlIcon";
import { Activity, UiItemActivity } from "./ActivitiesPanel";

const StyledActivityRoot = styled("div")<{ isActiveFound: boolean; isFound: boolean }>(({ theme, isActiveFound, isFound }) => ({
    margin: `${theme.spacing(1)} ${theme.spacing(1)} ${theme.spacing(2)}`,
    outline: isActiveFound ? "1px solid green" : isFound ? "1px solid red" : "none",
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
    ({ activity, isActiveItem }: { activity: Activity<UiItemActivity>; isActiveItem: boolean }, ref: ForwardedRef<HTMLDivElement>) => {
        const commentSettings = useSelector(getCommentSettings);

        const isHighlighted = ["SCENARIO_DEPLOYED", "SCENARIO_CANCELED"].includes(activity.type);

        return (
            <StyledActivityRoot ref={ref} isActiveFound={activity.ui.isActiveFound} isFound={activity.ui.isFound}>
                <StyledActivityHeader isHighlighted={isHighlighted} isActive={isActiveItem}>
                    <StyledHeaderIcon src={activity.activities.icon} />
                    <Typography variant={"caption"} sx={(theme) => ({ color: theme.palette.text.primary })}>
                        {activity.activities.displayableName}
                    </Typography>
                    {activity.actions.map((activityAction) => (
                        <HeaderActivity key={activityAction.id} activityAction={activityAction} />
                    ))}
                </StyledActivityHeader>
                <StyledActivityBody>
                    <Typography mt={0.5} component={"p"} variant={"overline"}>
                        {formatDateTime(activity.date)} | {activity.user}
                    </Typography>
                    {activity.scenarioVersionId && (
                        <Typography component={"p"} variant={"overline"}>
                            Version: {activity.scenarioVersionId}
                        </Typography>
                    )}
                    {activity.comment && <CommentContent content={activity.comment} commentSettings={commentSettings} />}
                    {activity.additionalFields.map((additionalField, index) => (
                        <Typography key={index} component={"p"} variant={"overline"}>
                            {additionalField.name}: {additionalField.value}
                        </Typography>
                    ))}
                </StyledActivityBody>
            </StyledActivityRoot>
        );
    },
);

ActivityItem.displayName = "ActivityItem";
