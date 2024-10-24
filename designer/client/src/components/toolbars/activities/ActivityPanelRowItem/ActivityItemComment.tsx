import React, { useState } from "react";
import CommentContent from "../../../comment/CommentContent";
import { ActionMetadata, ActivityComment, ActivityType } from "../types";
import { useDispatch, useSelector } from "react-redux";
import { createSelector } from "reselect";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { Box } from "@mui/material";
import { StyledActionIcon } from "./StyledActionIcon";
import { useActivityItemInfo } from "./ActivityItemProvider";
import * as DialogMessages from "../../../../common/DialogMessages";
import HttpService from "../../../../http/HttpService";
import { useTranslation } from "react-i18next";
import { useWindows } from "../../../../windowManager";
import { getProcessName } from "../../../../reducers/selectors/graph";
import { getScenarioActivities } from "../../../../actions/nk/scenarioActivities";
import { ActivityItemCommentModify } from "./ActivityItemCommentModify";

const getCommentSettings = createSelector(getFeatureSettings, (f) => f.commentSettings || {});

const CommentActivity = ({
    activityAction,
    scenarioActivityId,
    commentContent,
    activityType,
}: {
    activityAction: ActionMetadata;
    scenarioActivityId: string;
    commentContent: ActivityComment["content"];
    activityType: ActivityType;
}) => {
    const { t } = useTranslation();
    const { confirm } = useWindows();
    const processName = useSelector(getProcessName);
    const dispatch = useDispatch();

    switch (activityAction.id) {
        case "delete_comment": {
            return (
                <StyledActionIcon
                    data-testid={`delete-comment-icon`}
                    onClick={async () =>
                        confirm({
                            text: DialogMessages.deleteComment(),
                            onConfirmCallback: (confirmed) => {
                                confirmed &&
                                    HttpService.deleteActivityComment(processName, scenarioActivityId).then(({ status }) => {
                                        if (status === "success") {
                                            dispatch(getScenarioActivities(processName));
                                        }
                                    });
                            },
                            confirmText: t("panels.actions.process-unarchive.yes", "Yes"),
                            denyText: t("panels.actions.process-unarchive.no", "No"),
                        })
                    }
                    key={activityAction.id}
                    src={activityAction.icon}
                    title={activityAction.displayableName}
                />
            );
        }
        case "edit_comment": {
            return (
                <ActivityItemCommentModify
                    activityAction={activityAction}
                    activityType={activityType}
                    scenarioActivityId={scenarioActivityId}
                    commentContent={commentContent}
                    data-testid={`edit-comment-icon`}
                    key={activityAction.id}
                />
            );
        }
    }
};

interface Props {
    comment: ActivityComment;
    searchQuery: string;
    activityActions: ActionMetadata[];
    scenarioActivityId: string;
    activityType: ActivityType;
}

export const ActivityItemComment = ({ comment, searchQuery, activityActions, scenarioActivityId, activityType }: Props) => {
    const commentSettings = useSelector(getCommentSettings);
    const [isMultiline, setIsMultiline] = useState(false);
    const { isActivityHovered } = useActivityItemInfo();

    const multilineDetection = (ref: HTMLDivElement) => {
        if (ref?.clientHeight > 20) {
            setIsMultiline(true);
        }
    };

    return (
        <Box ref={multilineDetection} display="grid" gridTemplateColumns={isMultiline ? "1fr 10%" : "1fr 15%"} alignItems="flex-start">
            <CommentContent
                content={comment.content.value}
                commentSettings={commentSettings}
                searchWords={[searchQuery]}
                variant={"overline"}
            />
            {isActivityHovered && (
                <Box
                    display={"flex"}
                    alignItems={"flex-end"}
                    marginLeft={"auto"}
                    flexBasis={"10%"}
                    flexDirection={isMultiline ? "column-reverse" : "row"}
                >
                    {activityActions.map((activityAction) => (
                        <CommentActivity
                            key={activityAction.id}
                            activityAction={activityAction}
                            scenarioActivityId={scenarioActivityId}
                            commentContent={comment.content}
                            activityType={activityType}
                        />
                    ))}
                </Box>
            )}
        </Box>
    );
};
