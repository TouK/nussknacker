import React, { useCallback } from "react";
import { ActionMetadata, ActivityComment, ActivityType, ModifyActivityCommentMeta } from "../types";
import { useWindows, WindowKind } from "../../../../windowManager";
import { useSelector } from "react-redux";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { StyledActionIcon } from "./StyledActionIcon";

interface Props {
    commentContent: ActivityComment["content"];
    scenarioActivityId: string;
    activityType: ActivityType;
    activityAction: ActionMetadata;
    title: string;
    confirmButtonText: string;
}
export const ActivityItemCommentModify = ({
    commentContent,
    scenarioActivityId,
    activityType,
    activityAction,
    title,
    confirmButtonText,
    ...props
}: Props) => {
    const featuresSettings = useSelector(getFeatureSettings);
    const { open } = useWindows();

    const handleOpenModifyComment = useCallback(() => {
        const permittedModifyCommentTypes: ActivityType[] = ["SCENARIO_DEPLOYED", "SCENARIO_CANCELED", "SCENARIO_PAUSED"];

        open<ModifyActivityCommentMeta>({
            title,
            isModal: true,
            shouldCloseOnEsc: true,
            kind: WindowKind.modifyActivityComment,
            meta: {
                existingComment: commentContent.value,
                scenarioActivityId,
                placeholder: permittedModifyCommentTypes.includes(activityType)
                    ? featuresSettings?.deploymentCommentSettings?.exampleComment
                    : undefined,
                confirmButtonText,
            },
        });
    }, [
        activityType,
        commentContent.value,
        confirmButtonText,
        featuresSettings?.deploymentCommentSettings?.exampleComment,
        open,
        scenarioActivityId,
        title,
    ]);

    return (
        <StyledActionIcon
            data-testid={`add-comment-icon`}
            onClick={handleOpenModifyComment}
            key={activityAction.id}
            src={activityAction.icon}
            title={activityAction.displayableName}
            {...props}
        />
    );
};
